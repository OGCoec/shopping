-- ============================================
-- 文件名：000_base62_functions.sql
-- 说明：Base62 编解码函数，用于在数据库侧以可读形式查看 16 字节 BYTEA 主键
-- 约定：
-- 1. 算法与后端 com.example.ShoppingSystem.Utils.HybridIdCodec 完全一致；
-- 2. 字母表为 0-9A-Za-z（标准 Base62，区分大小写）；
-- 3. to_base62 将 16 字节按无符号大端整数转 Base62，对应 new BigInteger(1, bytes)；
-- 4. from_base62 将 Base62 字符串还原为 16 字节 BYTEA，便于按可读 ID 反查；
-- 5. 这些函数为只读展示/查询辅助，不参与业务写入链路。
-- 适配：PostgreSQL
-- ============================================

-- 将 16 字节 BYTEA 转为 Base62 字符串
CREATE OR REPLACE FUNCTION to_base62(p_id bytea) RETURNS text AS $$
DECLARE
    -- Base62 字母表，索引顺序与后端 HybridIdCodec 一致
    alphabet constant text := '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz';
    big_value numeric := 0;
    byte_index int;
    digit int;
    result text := '';
BEGIN
    IF p_id IS NULL THEN
        RETURN NULL;
    END IF;
    -- 按大端无符号整数累加，对应 new BigInteger(1, bytes)
    FOR byte_index IN 0 .. octet_length(p_id) - 1 LOOP
        big_value := big_value * 256 + get_byte(p_id, byte_index);
    END LOOP;
    IF big_value = 0 THEN
        RETURN '0';
    END IF;
    -- 反复除 62，余数映射字母表，最后高位在前
    WHILE big_value > 0 LOOP
        digit := (big_value % 62)::int;
        result := substr(alphabet, digit + 1, 1) || result;
        big_value := div(big_value, 62);
    END LOOP;
    RETURN result;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- 将 Base62 字符串还原为 16 字节 BYTEA，便于按可读 ID 反查
CREATE OR REPLACE FUNCTION from_base62(p_text text) RETURNS bytea AS $$
DECLARE
    alphabet constant text := '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz';
    big_value numeric := 0;
    char_index int;
    digit int;
    byte_value int;
    hex_text text := '';
BEGIN
    IF p_text IS NULL THEN
        RETURN NULL;
    END IF;
    -- 与后端 BASE62_PATTERN 校验保持一致
    IF p_text !~ '^[0-9A-Za-z]{1,22}$' THEN
        RAISE EXCEPTION 'Hybrid ID must be Base62: %', p_text;
    END IF;
    FOR char_index IN 1 .. length(p_text) LOOP
        digit := position(substr(p_text, char_index, 1) IN alphabet) - 1;
        IF digit < 0 THEN
            RAISE EXCEPTION 'Hybrid ID must be Base62: %', p_text;
        END IF;
        big_value := big_value * 62 + digit;
    END LOOP;
    -- 还原为固定 16 字节大端表示
    FOR char_index IN 1 .. 16 LOOP
        byte_value := (big_value % 256)::int;
        hex_text := lpad(to_hex(byte_value), 2, '0') || hex_text;
        big_value := div(big_value, 256);
    END LOOP;
    RETURN ('\x' || hex_text)::bytea;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

COMMENT ON FUNCTION to_base62(bytea) IS '将 16 字节 BYTEA 主键转为 Base62 字符串，算法与后端 HybridIdCodec 一致';
COMMENT ON FUNCTION from_base62(text) IS '将 Base62 字符串还原为 16 字节 BYTEA，便于按可读 ID 反查';
