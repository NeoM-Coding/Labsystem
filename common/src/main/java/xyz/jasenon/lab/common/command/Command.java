package xyz.jasenon.lab.common.command;

import xyz.jasenon.lab.common.command.checker.CheckType;

public class Command {

    /**
     * 命令行参数
     */
    private String commandLine;

    /**
     * 校验类型
     */
    private CheckType checkType;

    public Command(String commandLine, CheckType checkType) {
        this.commandLine = commandLine;
        this.checkType = checkType;
    }

    public String getCommandLine() {
        return commandLine;
    }

    public CheckType getCheckType() {
        return checkType;
    }
}
