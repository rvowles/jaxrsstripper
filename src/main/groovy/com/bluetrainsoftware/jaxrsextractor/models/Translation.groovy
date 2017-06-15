package com.bluetrainsoftware.jaxrsextractor.models

import groovy.transform.ToString

/**
 *
 * @author Richard Vowles - https://plus.google.com/+RichardVowles
 */
@ToString
class Translation {
	String packageName
	String sourceFolder
	String destinationFolder
	String destinationPackage
	boolean rewriteSource = true
	List<String> includes = []
	List<String> excludes = []
	Map<String, String> suffixes
}
