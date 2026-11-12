package org.unreal.modelrouter.deploy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 生命周期脚本的静态约束。
 *
 * <p>这些脚本没法在 CI 里真跑（要 root、要 systemd、要 Windows 服务管理器），所以这里守的是
 * <b>一旦被改坏就会静默降级的那几个值</b>：优雅停机的超时窗口、检测先于落盘、卸载默认不删数据。
 * 它们都是「看起来无所谓、出事才发现」的参数，比跑一遍脚本更值得钉住。</p>
 */
@DisplayName("生命周期脚本约束（issue #1108）")
class LifecycleAssetsTest {

    private static final Path LIFECYCLE = Paths.get("deploy", "lifecycle");
    private static final Path LINUX = LIFECYCLE.resolve("linux");
    private static final Path WINDOWS = LIFECYCLE.resolve("windows");

    /** Spring Boot 的 {@code timeout-per-shutdown-phase} 默认 30s，托管层必须比它宽。 */
    private static final long MIN_STOP_TIMEOUT_SECONDS = 35L;

    private static String read(Path path) throws IOException {
        return Files.readString(path);
    }

    private static long captureLong(String text, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        assertTrue(matcher.find(), "没有匹配到 " + regex + "，说明这条约束被删了或被改了写法");
        return Long.parseLong(matcher.group(1));
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = text.indexOf(needle);
        while (index >= 0) {
            count++;
            index = text.indexOf(needle, index + needle.length());
        }
        return count;
    }

    @Test
    @DisplayName("systemd unit 的停机超时大于 Spring 的优雅停机窗口")
    void systemdUnitKeepsGracefulShutdownWindow() throws IOException {
        String unit = read(LINUX.resolve("jairouter.service"));

        assertTrue(unit.contains("KillSignal=SIGTERM"),
                "必须显式声明 SIGTERM：换成 SIGKILL 后 server.shutdown=graceful 完全失效");
        long timeout = captureLong(unit, "TimeoutStopSec=(\\d+)");
        assertTrue(timeout >= MIN_STOP_TIMEOUT_SECONDS,
                "TimeoutStopSec=" + timeout + "s 不大于 spring.lifecycle.timeout-per-shutdown-phase（30s），"
                        + "systemd 会在 Spring 排空在途请求前补一刀 SIGKILL");
        assertTrue(unit.contains("SuccessExitStatus=143"),
                "被 SIGTERM 停掉时退出码是 143，不声明的话 systemctl stop 会被记成 failed");
        assertTrue(unit.contains("Restart=on-failure"), "崩溃后不重启，可用性靠人盯着");
        assertTrue(unit.contains("EnvironmentFile="),
                "密钥必须走 EnvironmentFile，不能写进 644 的 unit 文件");
        assertTrue(unit.contains("--spring.config.additional-location"),
                "外部配置要显式指定目录：Windows 风格的 /app/config 在 Linux 前缀下不存在");
    }

    @Test
    @DisplayName("安装脚本先检测后落盘，并保留多级降级")
    void installScriptDetectsBeforeTouchingDisk() throws IOException {
        String script = read(LINUX.resolve("install.sh"));

        assertTrue(script.startsWith("#!/usr/bin/env bash"), "缺 shebang 会被当 sh 跑，set -o pipefail 直接报错");
        assertTrue(script.contains("set -euo pipefail"), "缺严格模式，中途失败会继续往下写文件");
        assertTrue(script.contains("MIN_JAVA_MAJOR=17"), "Java 主版本下限必须显式声明（Boot 4 要求 17+）");
        assertTrue(script.contains("systemctl enable --now"), "systemd 路径要注册为开机自启");
        assertTrue(script.contains("docker info"),
                "只看 docker 命令存在不够：守护进程不可用时应当降级而不是装作装好了");
        assertTrue(script.contains("docker compose version"),
                "有 docker 不等于有 compose：只装了引擎、没有 compose 插件的机器"
                        + "（Ubuntu 的 docker.io 包就是这样）必须被判成容器模式不可用，"
                        + "否则要等到 `up -d` 才发现命令根本不存在");
        assertTrue(script.contains("docker-compose version"),
                "老机器只有独立的 docker-compose 命令，要能回退到它而不是直接放弃容器模式");
        assertTrue(script.contains("/actuator/health"), "装完必须真去请求健康端点");
        assertTrue(script.contains("useradd --system"), "systemd 路径要建独立运行用户，不能用 root 跑");

        int portCheck = script.indexOf("port_in_use");
        int firstMkdir = script.indexOf("mkdir -p \"$PREFIX/lib\"");
        assertTrue(portCheck > 0 && firstMkdir > 0, "端口检测或目录创建不见了");
        assertTrue(portCheck < firstMkdir,
                "端口检测必须早于创建目录：装前失败要不落文件，否则重试时残留半成品");
    }

    @Test
    @DisplayName("Linux 卸载默认只删程序，--purge 才删数据")
    void uninstallScriptKeepsDataUnlessPurge() throws IOException {
        String script = read(LINUX.resolve("uninstall.sh"));

        assertTrue(script.contains("disable --now"), "卸载要先停服务再删 unit");
        assertTrue(script.contains("--purge"), "缺 --purge 就变成「只能保数据」或「总是删数据」");
        assertTrue(script.contains("PostgreSQL"),
                "必须打印外部库被保留：不 DROP 表是刻意行为，得让运维看见");

        int purgeFunc = script.indexOf("purge_data()");
        int purgeBody = script.indexOf("rm -rf \"$PREFIX/data\"");
        assertTrue(purgeFunc > 0, "找不到 purge_data 函数");
        assertTrue(purgeBody > purgeFunc, "删数据的语句不在 purge_data 函数体里");
        assertTrue(script.contains("if [[ \"$PURGE\" == \"true\" ]]; then"),
                "purge_data 必须只在 --purge 分支被调用");
        assertEquals(1, countOccurrences(script, "rm -rf \"$PREFIX/data\""),
                "rm -rf data 只应出现一次（在 purge_data 里）；多出来说明有别的路径也在删数据");
    }

    @Test
    @DisplayName("安装脚本不碰别人的目录，多实例的环境文件互不共用")
    void installScriptRefusesForeignPrefix() throws IOException {
        String install = read(LINUX.resolve("install.sh"));
        String uninstall = read(LINUX.resolve("uninstall.sh"));

        assertTrue(install.contains("check_prefix_ownership"),
                "目标目录可能是别的部署的数据卷，必须在落盘前先判断归属");
        assertTrue(install.contains("jairouter.json"),
                "缺安装标记就没法区分「本脚本装的」与「别人的目录」");
        assertTrue(install.contains("--force"),
                "拒绝写入的同时要给出显式放行的出口，否则运维只能手工删目录");

        // 真正的判据是「plan_and_apply 里先判归属、后建目录」，不是函数定义的位置。
        int planStart = install.indexOf("plan_and_apply() {");
        int ownershipCall = install.indexOf("check_prefix_ownership", planStart);
        int mkdir = install.indexOf("mkdir -p \"$PREFIX/lib\"", planStart);
        assertTrue(planStart > 0 && ownershipCall > 0 && mkdir > 0 && ownershipCall < mkdir,
                "归属判断必须早于 mkdir：先建目录再判断就等于已经把别人的目录改了一半");

        // 环境文件名由 prefix 派生：同一台机器装第二个实例时不能复用第一个的密钥。
        assertTrue(install.contains("basename \"$PREFIX\""), "install.sh 的 env 文件名没有按 prefix 派生");
        assertTrue(uninstall.contains("basename \"$PREFIX\""), "uninstall.sh 的 env 文件名没有按 prefix 派生");
    }

    @Test
    @DisplayName("卸载失败时不假装成功")
    void uninstallScriptDoesNotClaimSuccessOnFailure() throws IOException {
        String script = read(LINUX.resolve("uninstall.sh"));

        assertTrue(script.contains("if ! detect_compose"),
                "compose 插件可能本来就不存在（容器是别处起的镜像），必须单独判断而不是假定命令可用");
        assertTrue(script.contains("容器可能仍在运行"),
                "停不掉容器却打印「已停止并移除容器与网络」，下一个人会以为环境已经干净了");

        // 「已停止并移除容器与网络」只能出现在 down 成功之后的分支里。
        int successLog = script.indexOf("已停止并移除容器与网络");
        int downCall = script.indexOf("down; then");
        assertTrue(successLog > 0 && downCall > 0 && downCall < successLog,
                "成功提示必须挂在 compose down 的真实退出码上，不能在调用之后无条件打印");
    }

    @Test
    @DisplayName("WinSW 模板把停机交给 SCM，超时同样大于 30s")
    void winswTemplateForwardsStopThroughScm() throws IOException {
        String xml = read(WINDOWS.resolve("jairouter-service.xml.template"));

        assertTrue(xml.contains("<executable>"), "WinSW 需要 executable 才能托管 java");
        assertTrue(xml.contains("<workingdirectory>"),
                "logback-spring.xml 用相对路径 logs，工作目录不写死会让日志散落");
        assertTrue(xml.contains("<stoptimeout>@@STOP_TIMEOUT_SEC@@sec</stoptimeout>"),
                "<stoptimeout> 必须留成占位符，由安装脚本填具体秒数");
        long timeout = captureLong(read(WINDOWS.resolve("install.ps1")), "\\$stopTimeout = (\\d+)");
        assertTrue(timeout >= MIN_STOP_TIMEOUT_SECONDS,
                "<stoptimeout>=" + timeout + "s 不大于 30s：WinSW 超时后结束整棵进程树，等于硬杀");
        assertTrue(xml.contains("delayedAutoStart"), "开机瞬间就起，容易在数据库还没就绪时白白失败一次");
        assertTrue(xml.contains("@@ENV_BLOCK@@"),
                "环境变量块留成占位符由安装脚本填：密钥不能直接写进模板文件");
    }

    @Test
    @DisplayName("Windows 安装脚本覆盖管理员检测与计划任务降级")
    void windowsInstallFallsBackWhenNotAdministrator() throws IOException {
        String script = read(WINDOWS.resolve("install.ps1"));

        assertTrue(script.contains("Test-Administrator"),
                "没有管理员权限时注册服务会失败，必须先检测再降级");
        assertTrue(script.contains("Get-JavaMajorVersion"), "缺 Java 版本检测，17 以下的机器会装上去再报错");
        assertTrue(script.contains("icacls"),
                "密钥文件必须收紧 ACL：Windows 上默认继承 Users 读权限");
        assertTrue(script.contains("Wait-Health"), "装完必须真去请求健康端点");
        assertTrue(script.contains("/actuator/health"));
        assertTrue(script.contains("schtasks /create"), "计划任务是没有管理员权限时的唯一自启手段");
        assertTrue(script.contains("jairouter.json"),
                "没有安装记录，卸载与巡检只能靠猜托管形态");
    }

    @Test
    @DisplayName("Windows 卸载默认保留数据，-Purge 才删")
    void windowsUninstallKeepsDataUnlessPurge() throws IOException {
        String script = read(WINDOWS.resolve("uninstall.ps1"));

        assertTrue(script.contains("schtasks /delete"), "计划任务卸载路径缺失");
        assertTrue(script.contains("-Purge"), "缺 -Purge 就变成「只能保数据」或「总是删数据」");
        assertTrue(script.contains("PostgreSQL"),
                "必须打印外部库被保留：不 DROP 表是刻意行为，得让运维看见");

        int purgeGuard = script.indexOf("if ($Purge)");
        int purgeBody = script.indexOf("Remove-Item $dir -Recurse -Force");
        assertTrue(purgeGuard > 0, "找不到 -Purge 判断");
        assertTrue(purgeBody > purgeGuard, "删数据目录的语句不在 -Purge 分支里");
    }

    @Test
    @DisplayName("README 记录 Windows 停机实测矩阵")
    void readmeStatesWindowsStopMatrix() throws IOException {
        String readme = read(LIFECYCLE.resolve("README.md"));

        assertTrue(readme.contains("taskkill /PID"),
                "必须写明 taskkill 不带 /F 也不会执行 shutdown hook —— 这是最容易踩的坑");
        assertTrue(readme.contains("1.43s"), "WinSW 的实测数字要不写，运维会怀疑「服务化能不能优雅停」");
        assertTrue(readme.contains("计划任务"), "计划任务不能优雅停机这条必须出现在文档里");
    }
}
