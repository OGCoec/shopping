package com.example.ShoppingSystem.service.captcha.tianai.impl;

import cloud.tianai.captcha.application.ImageCaptchaApplication;
import cloud.tianai.captcha.common.constant.CaptchaTypeConstant;
import cloud.tianai.captcha.resource.CrudResourceStore;
import cloud.tianai.captcha.resource.FontCache;
import cloud.tianai.captcha.resource.ImageCaptchaResourceManager;
import cloud.tianai.captcha.resource.ResourceStore;
import cloud.tianai.captcha.resource.common.model.dto.Resource;
import cloud.tianai.captcha.resource.common.model.dto.ResourceMap;
import cloud.tianai.captcha.resource.impl.provider.FileResourceProvider;
import com.example.ShoppingSystem.service.captcha.tianai.TianaiCaptchaResourceInitService;
import com.example.ShoppingSystem.service.captcha.tianai.generator.SliderTemplateImageGenerator;
import com.example.ShoppingSystem.service.captcha.tianai.resource.CaptchaRedisKeys;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static cloud.tianai.captcha.common.constant.CommonConstant.DEFAULT_TAG;
import static cloud.tianai.captcha.generator.impl.StandardSliderImageCaptchaGenerator.TEMPLATE_ACTIVE_IMAGE_NAME;
import static cloud.tianai.captcha.generator.impl.StandardSliderImageCaptchaGenerator.TEMPLATE_FIXED_IMAGE_NAME;
import static cloud.tianai.captcha.generator.impl.StandardSliderImageCaptchaGenerator.TEMPLATE_MASK_IMAGE_NAME;

/**
 * 天爱验证码资源初始化服务。
 * 负责在项目启动时重建验证码背景图、滑块模板、旋转模板和字体资源的 Redis 配置。
 */
@Slf4j
@Service
public class TianaiCaptchaResourceInitServiceImpl implements TianaiCaptchaResourceInitService {

    private static final String ROTATE_BACKGROUND_DIR = "C:/Users/damn/Desktop/shopping/shopping-web/src/main/resources/captcha/tianai/rotate/background";
    private static final Duration INIT_LOCK_TTL = Duration.ofMinutes(2);
    private static final String REDIS_RESOURCE_STORE_CLASS = "cloud.tianai.captcha.spring.plugins.RedisResourceStore";
    private static final String DEFAULT_TEMPLATE_PATH_PREFIX = "META-INF/cut-image/template";
    private static final String TEMPLATE_RESOURCE_TYPE = "classpath";
    private static final String FILE_RESOURCE_TYPE = "file";
    private static final String CUSTOM_SLIDER_TEMPLATE_DIR =
            "C:/Users/damn/Desktop/shopping/shopping-web/src/main/resources/captcha/tianai/rotate/spilt";
    private static final String GENERATED_SLIDER_TEMPLATE_DIR = CUSTOM_SLIDER_TEMPLATE_DIR + "/generated";
    private static final int ROTATE_MIN_BACKGROUND_HEIGHT = 200;

    private final ImageCaptchaApplication imageCaptchaApplication;
    private final StringRedisTemplate stringRedisTemplate;
    private final Gson gson = new Gson();
    private final SliderTemplateImageGenerator sliderTemplateImageGenerator = new SliderTemplateImageGenerator();

    /**
     * 注入天爱验证码应用对象和 Redis 操作模板。
     *
     * @param imageCaptchaApplication 天爱验证码入口对象，用于获取资源管理器
     * @param stringRedisTemplate Redis 字符串模板，用于覆盖重建资源配置 key
     */
    public TianaiCaptchaResourceInitServiceImpl(ImageCaptchaApplication imageCaptchaApplication,
                                                StringRedisTemplate stringRedisTemplate) {
        this.imageCaptchaApplication = imageCaptchaApplication;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 初始化天爱验证码资源。
     * 会读取本地背景图目录，注册 SLIDER、ROTATE、CONCAT、WORD_IMAGE_CLICK 的背景资源，
     * 同时重建默认字体资源和模板资源；Redis 存储场景下使用临时 key + rename 保证覆盖写入。
     */
    @Override
    public void initializeRotateResources() {
        // 先获取 Tianai 底层资源管理器；后续资源导入和模板注册都依赖它。
        ImageCaptchaResourceManager resourceManager = imageCaptchaApplication.getImageCaptchaResourceManager();
        if (resourceManager == null) {
            log.warn("Tianai captcha resource manager is null, skip resource initialization");
            return;
        }

        // 注册文件资源提供器，允许 Tianai 识别本地磁盘上的图片路径。
        resourceManager.registerResourceProvider(new FileResourceProvider());

        // 资源存储外层可能包了一层字体缓存，这里先解开拿到底层可写存储。
        ResourceStore store = resourceManager.getResourceStore();
        if (store instanceof FontCache fontCache) {
            store = fontCache.getTarget();
        }

        // 只有 CrudResourceStore 支持 addResource/addTemplate 等写操作，非可写存储直接跳过。
        if (!(store instanceof CrudResourceStore crudResourceStore)) {
            log.warn("Current Tianai resource store does not support write operations, skip initialization");
            return;
        }

        // 扫描本地背景图目录，目前只导入 png 文件，并按文件名排序保证初始化顺序稳定。
        File backgroundDir = new File(ROTATE_BACKGROUND_DIR);
        File[] backgroundFiles = backgroundDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".png"));
        if (backgroundFiles == null || backgroundFiles.length == 0) {
            log.warn("Background image directory is empty, skip custom background import: {}", ROTATE_BACKGROUND_DIR);
            return;
        }
        Arrays.sort(backgroundFiles, Comparator.comparing(File::getName));

        String[] types = {
                CaptchaTypeConstant.SLIDER,
                CaptchaTypeConstant.ROTATE,
                CaptchaTypeConstant.CONCAT,
                CaptchaTypeConstant.WORD_IMAGE_CLICK
        };

        // 所有背景图都会先生成通用 payload，供非 ROTATE 类型直接复用。
        List<String> payloads = Arrays.stream(backgroundFiles)
                .map(File::getAbsolutePath)
                .map(path -> gson.toJson(new Resource("file", path)))
                .toList();
        // ROTATE 会额外做高度筛选，避免模板裁剪时越界。
        List<String> rotatePayloads = Arrays.stream(backgroundFiles)
                .filter(this::isRotateCompatibleBackground)
                .map(File::getAbsolutePath)
                .map(path -> gson.toJson(new Resource("file", path)))
                .toList();
        if (rotatePayloads.isEmpty()) {
            log.warn("No ROTATE background image height is >= {}, ROTATE resource key will not be overwritten",
                    ROTATE_MIN_BACKGROUND_HEIGHT);
        }

        // Redis 存储要走“临时 key + rename”的整体替换流程，避免初始化中途读到半成品。
        if (isRedisCrudResourceStore(crudResourceStore)) {
            rebuildResourcesWithLock(types, payloads, rotatePayloads, buildDefaultResourcePayloads(), buildDefaultTemplatePayloads());
        } else {
            // 非 Redis 存储直接往当前 store 追加默认资源和背景图即可。
            addDefaultResources(crudResourceStore);
            addDefaultTemplates(crudResourceStore);
            for (File backgroundFile : backgroundFiles) {
                for (String type : types) {
                    // 旋转验证码只接收满足高度要求的背景图，其它类型暂时不做这层过滤。
                    if (CaptchaTypeConstant.ROTATE.equals(type) && !isRotateCompatibleBackground(backgroundFile)) {
                        continue;
                    }
                    crudResourceStore.addResource(type, new Resource("file", backgroundFile.getAbsolutePath()));
                }
            }
        }

        log.info("Registered {} background images for {} captcha types, ROTATE compatible count={}",
                backgroundFiles.length, types.length, rotatePayloads.size());
    }

    /**
     * 判断当前天爱资源存储是否为 RedisResourceStore。
     * 这里使用 AOP 终极目标类判断，避免资源存储被切面代理后类名判断失效。
     *
     * @param crudResourceStore 当前天爱资源存储对象
     * @return true 表示底层存储是 RedisResourceStore
     */
    private boolean isRedisCrudResourceStore(CrudResourceStore crudResourceStore) {
        // 优先拿到代理背后的真实类型，避免 AOP 包装后类名判断失真。
        Class<?> storeClass = AopProxyUtils.ultimateTargetClass(crudResourceStore);
        if (storeClass == null) {
            // 如果没有代理信息，就退回到当前实例自身的运行时类型。
            storeClass = crudResourceStore.getClass();
        }
        return REDIS_RESOURCE_STORE_CLASS.equals(storeClass.getName());
    }

    /**
     * 加分布式锁后重建 Redis 中的天爱验证码资源配置。
     * 同一时间只允许一个节点执行覆盖重建，避免多实例启动时重复写入或互相覆盖。
     *
     * @param types 需要写入背景资源的验证码类型
     * @param payloads 通用背景资源 JSON 列表
     * @param rotatePayloads 经过 ROTATE 尺寸过滤后的背景资源 JSON 列表
     * @param defaultResourcePayloads 默认资源配置，例如字体资源
     * @param defaultTemplatePayloads 默认模板配置，例如 SLIDER/ROTATE 模板
     */
    private void rebuildResourcesWithLock(String[] types,
                                          List<String> payloads,
                                          List<String> rotatePayloads,
                                          Map<String, List<String>> defaultResourcePayloads,
                                          Map<String, List<String>> defaultTemplatePayloads) {
        // 锁值使用随机 UUID，释放时可以校验“是不是自己加的锁”。
        String lockValue = UUID.randomUUID().toString();
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(CaptchaRedisKeys.RESOURCE_DEFAULT_INIT_LOCK, lockValue, INIT_LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            log.warn("Skip Tianai resource rebuild because another node is already rebuilding the resource keys");
            return;
        }

        try {
            // 真正的重建工作统一收口到一个方法里，减少锁保护范围内的散乱逻辑。
            writeTempKeysAndRename(types, payloads, rotatePayloads, defaultResourcePayloads, defaultTemplatePayloads, lockValue);
            log.info("Tianai built-in resources and custom background resources have been rebuilt");
        } finally {
            // 无论是否异常，都按“比对锁值”的方式安全释放锁。
            releaseInitLock(lockValue);
        }
    }

    /**
     * 使用 pipeline 写临时 key，并通过 rename 原子替换正式 key。
     * 这个流程避免先删除正式 key 后再写入造成读线程看到空资源或半成品资源。
     *
     * @param types 需要重建的验证码类型
     * @param payloads 通用背景资源 JSON 列表
     * @param rotatePayloads ROTATE 专用背景资源 JSON 列表
     * @param defaultResourcePayloads 默认资源配置
     * @param defaultTemplatePayloads 默认模板配置
     * @param lockValue 当前锁值，同时用于构造临时 key 后缀
     */
    private void writeTempKeysAndRename(String[] types,
                                        List<String> payloads,
                                        List<String> rotatePayloads,
                                        Map<String, List<String>> defaultResourcePayloads,
                                        Map<String, List<String>> defaultTemplatePayloads,
                                        String lockValue) {
        stringRedisTemplate.executePipelined(new SessionCallback<Object>() {
            /**
             * 在同一个 Redis pipeline 中完成临时 key 删除、资源写入和 rename 覆盖。
             *
             * @param operations pipeline 内部 Redis 操作对象
             * @return SessionCallback 固定返回值，业务不使用
             */
            @Override
            @SuppressWarnings({"rawtypes", "unchecked"})
            public Object execute(RedisOperations operations) {
                // 第 1 步：先把各验证码类型的背景资源写到对应的临时 key。
                for (String type : types) {
                    String tempKey = buildResourceTempKey(type, lockValue);
                    List<String> typePayloads = payloadsForType(type, payloads, rotatePayloads);
                    operations.delete(tempKey);
                    if (!typePayloads.isEmpty()) {
                        operations.opsForList().rightPushAll(tempKey, typePayloads);
                    }
                }
                // 第 2 步：写入字体等默认资源，它们和业务背景图分开维护。
                for (Map.Entry<String, List<String>> entry : defaultResourcePayloads.entrySet()) {
                    String tempKey = buildResourceTempKey(entry.getKey(), lockValue);
                    operations.delete(tempKey);
                    if (!entry.getValue().isEmpty()) {
                        operations.opsForList().rightPushAll(tempKey, entry.getValue());
                    }
                }
                // 第 3 步：写入默认模板和本地扩展模板。
                for (Map.Entry<String, List<String>> entry : defaultTemplatePayloads.entrySet()) {
                    String tempKey = buildTemplateTempKey(entry.getKey(), lockValue);
                    operations.delete(tempKey);
                    if (!entry.getValue().isEmpty()) {
                        operations.opsForList().rightPushAll(tempKey, entry.getValue());
                    }
                }
                // 第 4 步：等所有临时 key 准备好之后，再 rename 到正式 key，实现整体替换。
                for (String type : types) {
                    if (!payloadsForType(type, payloads, rotatePayloads).isEmpty()) {
                        operations.rename(buildResourceTempKey(type, lockValue), CaptchaRedisKeys.resourceDefaultKey(type));
                    }
                }
                for (String type : defaultResourcePayloads.keySet()) {
                    operations.rename(buildResourceTempKey(type, lockValue), CaptchaRedisKeys.resourceDefaultKey(type));
                }
                for (String type : defaultTemplatePayloads.keySet()) {
                    operations.rename(buildTemplateTempKey(type, lockValue), CaptchaRedisKeys.templateDefaultKey(type));
                }
                return null;
            }
        });
    }

    /**
     * 根据验证码类型选择应该写入 Redis 的背景资源列表。
     * ROTATE 使用高度过滤后的资源，其它类型暂时使用通用背景资源。
     *
     * @param type 验证码类型
     * @param payloads 通用背景资源 JSON 列表
     * @param rotatePayloads ROTATE 专用背景资源 JSON 列表
     * @return 当前验证码类型对应的资源 JSON 列表
     */
    private List<String> payloadsForType(String type, List<String> payloads, List<String> rotatePayloads) {
        // 目前只有 ROTATE 使用专用背景图列表，其它类型全部走通用背景图集合。
        return CaptchaTypeConstant.ROTATE.equals(type) ? rotatePayloads : payloads;
    }

    /**
     * 判断背景图是否适合 ROTATE 旋转验证码。
     * Tianai 默认旋转模板高度约为 200px，过矮的背景图会导致裁剪坐标越界。
     *
     * @param backgroundFile 候选背景图文件
     * @return true 表示可以写入 ROTATE 背景资源池
     */
    private boolean isRotateCompatibleBackground(File backgroundFile) {
        try {
            // 先尝试把文件解码成图片，损坏文件或假后缀文件会在这里被过滤掉。
            BufferedImage image = ImageIO.read(backgroundFile);
            if (image == null) {
                log.warn("Skip ROTATE background because it is not a readable image: {}", backgroundFile.getAbsolutePath());
                return false;
            }
            // 旋转模板高度接近 200px，背景图过矮时裁剪区域容易越界。
            int height = image.getHeight();
            if (height < ROTATE_MIN_BACKGROUND_HEIGHT) {
                log.warn("Skip ROTATE background {} because height {} < {}",
                        backgroundFile.getName(), height, ROTATE_MIN_BACKGROUND_HEIGHT);
                return false;
            }
            return true;
        } catch (IOException e) {
            log.warn("Skip ROTATE background because it cannot be read: {}, error={}",
                    backgroundFile.getAbsolutePath(), e.getMessage());
            return false;
        }
    }

    /**
     * 构建默认资源配置。
     * 当前只写入字体资源，供 WORD_IMAGE_CLICK 文字点选验证码生成中文字体图片使用。
     *
     * @return key 为资源类型、value 为资源 JSON 列表的配置集合
     */
    private Map<String, List<String>> buildDefaultResourcePayloads() {
        Map<String, List<String>> resources = new LinkedHashMap<>();
        // 文字点选验证码需要中文字体来渲染提示字图，这里固定注入默认字体资源。
        Resource fontResource = new Resource(TEMPLATE_RESOURCE_TYPE, DEFAULT_TEMPLATE_PATH_PREFIX + "/fonts/SIMSUN.TTC");
        resources.put(FontCache.FONT_TYPE, List.of(gson.toJson(fontResource)));
        return resources;
    }

    /**
     * 构建默认模板配置。
     * SLIDER 会包含 Tianai 内置模板和本地自定义滑块模板，ROTATE 保留 Tianai 内置旋转模板。
     *
     * @return key 为验证码类型、value 为模板 JSON 列表的配置集合
     */
    private Map<String, List<String>> buildDefaultTemplatePayloads() {
        Map<String, List<String>> templates = new LinkedHashMap<>();
        // SLIDER 同时包含内置模板和本地扩展模板，ROTATE 暂时保留一套内置模板。
        templates.put(CaptchaTypeConstant.SLIDER, buildSliderTemplatePayloads());
        templates.put(CaptchaTypeConstant.ROTATE, List.of(gson.toJson(buildTemplateResourceMap("rotate_1"))));
        return templates;
    }

    /**
     * 构建 SLIDER 滑动验证码模板 JSON 列表。
     * 默认加入 Tianai 的 slider_1、slider_2，再追加自定义模板目录中的所有可读图片。
     *
     * @return SLIDER 模板 JSON 列表
     */
    private List<String> buildSliderTemplatePayloads() {
        List<String> payloads = new ArrayList<>();
        // 先加入 Tianai 内置模板，保证自定义模板目录为空时仍然有基础模板可用。
        payloads.add(gson.toJson(buildTemplateResourceMap("slider_1")));
        payloads.add(gson.toJson(buildTemplateResourceMap("slider_2")));
        // 再把本地扩展模板逐个转成 ResourceMap 并追加到最终列表。
        customSliderTemplateDirectories().stream()
                .map(this::buildFileSliderTemplateResourceMap)
                .map(gson::toJson)
                .forEach(payloads::add);
        return payloads;
    }

    /**
     * 构建 Tianai classpath 内置模板资源。
     * 模板目录下必须包含 active.png 和 fixed.png 两个文件。
     *
     * @param templateDirName Tianai 内置模板目录名，例如 slider_1、slider_2、rotate_1
     * @return Tianai 模板资源映射
     */
    private ResourceMap buildTemplateResourceMap(String templateDirName) {
        ResourceMap template = new ResourceMap(DEFAULT_TAG, 4);
        // classpath 模板按 Tianai 约定只需要 active/fixed 两张图。
        String templatePath = DEFAULT_TEMPLATE_PATH_PREFIX + "/" + templateDirName;
        template.put(TEMPLATE_ACTIVE_IMAGE_NAME, new Resource(TEMPLATE_RESOURCE_TYPE, templatePath + "/active.png"));
        template.put(TEMPLATE_FIXED_IMAGE_NAME, new Resource(TEMPLATE_RESOURCE_TYPE, templatePath + "/fixed.png"));
        return template;
    }

    /**
     * 使用本地图片文件构建自定义 SLIDER 模板资源。
     * 同一张图片同时作为 active.png 和 fixed.png，表示滑块形状和缺口形状一致。
     *
     * @param templateFile 本地自定义滑块模板图片
     * @return 文件型 SLIDER 模板资源映射
     */
    private ResourceMap buildFileSliderTemplateResourceMap(File templateDir) {
        ResourceMap template = new ResourceMap(DEFAULT_TAG, 4);
        // 本地生成后的模板目录会同时包含 active/fixed/mask 三张图。
        String templatePath = templateDir.getAbsolutePath();
        template.put(TEMPLATE_ACTIVE_IMAGE_NAME, new Resource(FILE_RESOURCE_TYPE, templatePath + "/" + TEMPLATE_ACTIVE_IMAGE_NAME));
        template.put(TEMPLATE_FIXED_IMAGE_NAME, new Resource(FILE_RESOURCE_TYPE, templatePath + "/" + TEMPLATE_FIXED_IMAGE_NAME));
        template.put(TEMPLATE_MASK_IMAGE_NAME, new Resource(FILE_RESOURCE_TYPE, templatePath + "/" + TEMPLATE_MASK_IMAGE_NAME));
        return template;
    }

    /**
     * 读取自定义 SLIDER 模板目录下的所有可用图片。
     * 会按文件名排序，保证每次启动写入 Redis 的顺序稳定。
     *
     * @return 可读的自定义滑块模板图片文件列表
     */
    private List<File> customSliderTemplateDirectories() {
        File templateDir = new File(CUSTOM_SLIDER_TEMPLATE_DIR);
        // 先按扩展名做一层粗过滤，减少无关文件参与后续图片解码。
        File[] templateFiles = templateDir.listFiles((dir, name) -> isSupportedImageFileName(name));
        if (templateFiles == null || templateFiles.length == 0) {
            log.warn("Custom SLIDER template directory is empty, only built-in templates will be used: {}",
                    CUSTOM_SLIDER_TEMPLATE_DIR);
            return List.of();
        }
        return Arrays.stream(templateFiles)
                // 再确认图片真的可读，过滤损坏文件或伪装扩展名文件。
                .filter(this::isReadableImage)
                // 按文件名排序，保证不同机器重建出来的模板顺序一致。
                .sorted(Comparator.comparing(File::getName))
                // 每张源图会被切成一个独立模板目录。
                .map(this::generateSliderTemplateDirectory)
                .filter(templateDirectory -> templateDirectory != null)
                .toList();
    }

    private File generateSliderTemplateDirectory(File sourceTemplateFile) {
        try {
            // 调用模板生成器，把单张源图切成 Tianai 约定的模板目录结构。
            return sliderTemplateImageGenerator.generate(sourceTemplateFile, new File(GENERATED_SLIDER_TEMPLATE_DIR));
        } catch (IOException e) {
            log.warn("Skip custom SLIDER template because generated files cannot be created: {}, error={}",
                    sourceTemplateFile.getAbsolutePath(), e.getMessage());
            return null;
        }
    }

    /**
     * 判断文件名是否是支持的图片后缀。
     * 当前支持 PNG、JPG、JPEG，实际可用性还会由 ImageIO 再读取校验。
     *
     * @param name 文件名
     * @return true 表示后缀属于支持范围
     */
    private boolean isSupportedImageFileName(String name) {
        // 这里只看后缀，真正是否能解码由 isReadableImage 再做一次确认。
        String lowerCaseName = name.toLowerCase();
        return lowerCaseName.endsWith(".png")
                || lowerCaseName.endsWith(".jpg")
                || lowerCaseName.endsWith(".jpeg");
    }

    /**
     * 判断图片文件是否能被 Java ImageIO 正常读取。
     * 用于跳过损坏文件、假后缀文件或当前运行时不支持的图片格式。
     *
     * @param imageFile 候选图片文件
     * @return true 表示图片可读
     */
    private boolean isReadableImage(File imageFile) {
        try {
            // 能被 ImageIO 正常解码，才允许继续参与模板生成。
            BufferedImage image = ImageIO.read(imageFile);
            if (image == null) {
                log.warn("Skip custom SLIDER template because it is not a readable image: {}",
                        imageFile.getAbsolutePath());
                return false;
            }
            return true;
        } catch (IOException e) {
            log.warn("Skip custom SLIDER template because it cannot be read: {}, error={}",
                    imageFile.getAbsolutePath(), e.getMessage());
            return false;
        }
    }

    /**
     * 向非 Redis 的天爱资源存储追加默认资源。
     * 当前只追加字体资源，主要兼容非 RedisResourceStore 场景。
     *
     * @param crudResourceStore 天爱可写资源存储
     */
    private void addDefaultResources(CrudResourceStore crudResourceStore) {
        // 非 Redis 存储场景没有“整体重建”的问题，直接补默认字体资源即可。
        crudResourceStore.addResource(FontCache.FONT_TYPE,
                new Resource(TEMPLATE_RESOURCE_TYPE, DEFAULT_TEMPLATE_PATH_PREFIX + "/fonts/SIMSUN.TTC"));
    }

    /**
     * 向非 Redis 的天爱资源存储追加默认模板。
     * 包含内置 SLIDER 模板、自定义 SLIDER 模板和内置 ROTATE 模板。
     *
     * @param crudResourceStore 天爱可写资源存储
     */
    private void addDefaultTemplates(CrudResourceStore crudResourceStore) {
        // 先补两套内置滑块模板，保证基础模板池始终存在。
        crudResourceStore.addTemplate(CaptchaTypeConstant.SLIDER, buildTemplateResourceMap("slider_1"));
        crudResourceStore.addTemplate(CaptchaTypeConstant.SLIDER, buildTemplateResourceMap("slider_2"));
        // 再把本地扩展模板追加进去，扩充模板池。
        customSliderTemplateDirectories()
                .forEach(templateDir -> crudResourceStore.addTemplate(
                        CaptchaTypeConstant.SLIDER,
                        buildFileSliderTemplateResourceMap(templateDir)
                ));
        // 旋转模板当前只保留一套内置模板。
        crudResourceStore.addTemplate(CaptchaTypeConstant.ROTATE, buildTemplateResourceMap("rotate_1"));
    }

    /**
     * 构建资源配置临时 key。
     * 临时 key 写完整后会通过 rename 替换正式资源 key。
     *
     * @param type 验证码类型或资源类型
     * @param lockValue 当前初始化锁值，用于区分不同重建任务的临时 key
     * @return 资源配置临时 Redis key
     */
    private String buildResourceTempKey(String type, String lockValue) {
        // 统一走 key 工具类，避免业务侧手拼 key 带来命名不一致。
        return CaptchaRedisKeys.resourceDefaultTempKey(type, lockValue);
    }

    /**
     * 构建模板配置临时 key。
     * 临时 key 写完整后会通过 rename 替换正式模板 key。
     *
     * @param type 验证码类型
     * @param lockValue 当前初始化锁值，用于区分不同重建任务的临时 key
     * @return 模板配置临时 Redis key
     */
    private String buildTemplateTempKey(String type, String lockValue) {
        // 模板 key 与资源 key 分开生成，便于分别覆盖和排查。
        return CaptchaRedisKeys.templateDefaultTempKey(type, lockValue);
    }

    /**
     * 释放初始化分布式锁。
     * 释放前先比对锁值，避免误删其它节点后来获取到的新锁。
     *
     * @param lockValue 当前节点持有的锁值
     */
    private void releaseInitLock(String lockValue) {
        // 先校验当前锁值是不是自己加的，避免误删其它节点后来拿到的锁。
        String currentLockValue = stringRedisTemplate.opsForValue().get(CaptchaRedisKeys.RESOURCE_DEFAULT_INIT_LOCK);
        if (lockValue.equals(currentLockValue)) {
            stringRedisTemplate.delete(CaptchaRedisKeys.RESOURCE_DEFAULT_INIT_LOCK);
        }
    }
}
