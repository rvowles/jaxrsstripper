package com.bluetrainsoftware.jaxrsextractor.models

import groovy.transform.ToString
import org.yaml.snakeyaml.TypeDescription
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.Constructor

/**
 *
 * @author Richard Vowles - https://plus.google.com/+RichardVowles
 */
@ToString
class ModelReader {
	List<Translation> translations

	static ModelReader loadTranslations(String filename) {
		Constructor cModel = new Constructor(ModelReader)
		TypeDescription typeModel = new TypeDescription(ModelReader)
		typeModel.putListPropertyType('translations', Translation)
		cModel.addTypeDescription(typeModel)

		Yaml parser = new Yaml(cModel)

		return (ModelReader)parser.load(new File(filename).text)
	}


}
