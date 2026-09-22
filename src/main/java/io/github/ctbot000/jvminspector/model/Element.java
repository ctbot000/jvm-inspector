package io.github.ctbot000.jvminspector.model;

/**
 * A node in a report. A report is a tree of sections; every other element is a leaf that carries
 * one piece of information in a shape a renderer knows how to lay out.
 */
public sealed interface Element permits Section, Property, Table, Note, Code {
}
