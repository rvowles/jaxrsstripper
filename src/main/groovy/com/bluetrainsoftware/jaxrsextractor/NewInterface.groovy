package com.bluetrainsoftware.jaxrsextractor

import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.ImportDeclaration
import com.github.javaparser.ast.Modifier
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.body.BodyDeclaration
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.TypeDeclaration
import com.github.javaparser.ast.expr.AnnotationExpr
import com.github.javaparser.ast.expr.MarkerAnnotationExpr
import com.github.javaparser.ast.expr.Name
import com.github.javaparser.ast.expr.NameExpr
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr
import groovy.transform.CompileStatic

/**
 *
 * @author Richard Vowles - https://plus.google.com/+RichardVowles
 */
@CompileStatic
class NewInterface {
	public static final List<String> HEADER_ANNOTATIONS = [ "Produces", "Consumes", "Path"]

	final CompilationUnit newInterface;
	final ClassOrInterfaceDeclaration newInterfaceType;

	public List<BodyDeclaration> methodBodies = new ArrayList<BodyDeclaration>();

	public NewInterface(CompilationUnit existingFile, ClassOrInterfaceDeclaration existingType) {
		newInterface = new CompilationUnit();

		newInterface.setPackageDeclaration(existingFile.packageDeclaration.get());
		NodeList<ImportDeclaration> imports = new NodeList<>();
		imports.addAll(existingFile.imports)
		imports.add(new ImportDeclaration(new Name("javax.ws.rs.Produces"), false, false))
		imports.add(new ImportDeclaration(new Name("javax.ws.rs.Consumes"), false, false))
		imports.add(new ImportDeclaration(new Name("javax.ws.rs.core.MediaType"), false, false))
		imports.add(new ImportDeclaration(new Name("io.swagger.annotations.Api"), false, false))
		newInterface.setImports(imports)

		// find all the jaxrs annotations
		NodeList<AnnotationExpr> interfaceAnnotation =
			NodeList.<AnnotationExpr> nodeList(existingType.annotations.findAll({ ae -> HEADER_ANNOTATIONS.contains(ae.name.asString()) }))

		// remove found annotations from existing type
		existingType.setAnnotations(NodeList.<AnnotationExpr> nodeList(existingType.annotations.findAll({it -> !interfaceAnnotation.contains(it)})));

		newInterfaceType = new ClassOrInterfaceDeclaration(EnumSet.of(Modifier.PUBLIC), true, "I" + existingType.name.asString());

		if (!interfaceAnnotation.find({it.nameAsString == 'Produces'})) {
			interfaceAnnotation.add(new SingleMemberAnnotationExpr(new Name("Produces"), new NameExpr("MediaType.APPLICATION_JSON")))
		}

		if (!interfaceAnnotation.find({it.nameAsString == 'Consumes'})) {
			interfaceAnnotation.add(new SingleMemberAnnotationExpr(new Name("Consumes"), new NameExpr("MediaType.APPLICATION_JSON")))
		}

		interfaceAnnotation.add(new MarkerAnnotationExpr("Api"))
		newInterfaceType.setAnnotations(interfaceAnnotation)

		NodeList<TypeDeclaration> newInterfaceTypes = NodeList.<TypeDeclaration> nodeList([newInterfaceType] as List<TypeDeclaration>)

		newInterface.setTypes(newInterfaceTypes);
	}
}
