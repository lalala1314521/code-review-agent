package io.github.lalala1314521.codereviewagent.model;

/**
 * diff 行类型。
 *
 * <p>ADD：新增行（审查对象——问题只可能引入在新增代码上）；
 * DEL：删除行；CONTEXT：上下文行（规则扫描时用于判断前后文，如空 catch）。
 */
public enum LineType {
    ADD,
    DEL,
    CONTEXT
}
