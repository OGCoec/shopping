常用命令（Windows 环境，当前会话 shell 使用 bash 语法）：
- 列出项目根目录：ls "/c/Users/damn/Desktop/shopping"
- 聚合编译 service 和 mapper：mvn -f "/c/Users/damn/Desktop/shopping/pom.xml" -DskipTests -pl shopping-service,shopping-mapper -am compile
- 聚合编译 web：mvn -f "/c/Users/damn/Desktop/shopping/pom.xml" -DskipTests -pl shopping-web -am compile
- 若需要整体构建：mvn -f "/c/Users/damn/Desktop/shopping/pom.xml" compile
- 项目不是 git 仓库，不能使用 git diff 作为默认变更来源。