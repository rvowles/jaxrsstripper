package com.bluetrainsoftware.jaxrsextractor

import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import groovy.transform.CompileStatic

/**
 *
 * @author Richard Vowles - https://plus.google.com/+RichardVowles
 */
@CompileStatic
class Resource {
	NewInterface newInterface
	File originalFile
	String originalFullName
	String newFullName
	CompilationUnit originalCompilationUnit
	ClassOrInterfaceDeclaration originalType
}
