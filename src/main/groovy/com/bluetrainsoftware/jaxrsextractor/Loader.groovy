package com.bluetrainsoftware.jaxrsextractor

import com.bluetrainsoftware.jaxrsextractor.models.ModelReader
import com.bluetrainsoftware.jaxrsextractor.models.Translation
import com.github.javaparser.JavaParser
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.ImportDeclaration
import com.github.javaparser.ast.Modifier
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.PackageDeclaration
import com.github.javaparser.ast.body.BodyDeclaration
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.body.Parameter
import com.github.javaparser.ast.body.TypeDeclaration
import com.github.javaparser.ast.expr.AnnotationExpr
import com.github.javaparser.ast.type.ClassOrInterfaceType
import com.github.javaparser.ast.type.Type
import groovy.transform.CompileStatic

@CompileStatic
public class Loader {

	private final Translation translation
	private File destinationPackage

	public Loader(Translation translation) {
		this.translation = translation
	}

	private class NewInterface {

		private final CompilationUnit newInterface;
		private final ClassOrInterfaceDeclaration newInterfaceType;

		public List<BodyDeclaration> methodBodies = new ArrayList<BodyDeclaration>();

		public NewInterface(CompilationUnit existingFile, TypeDeclaration existingType) {
			newInterface = new CompilationUnit();
			newInterface.setPackageDeclaration(new PackageDeclaration(JavaParser.parseName(translation.destinationPackage)));
			newInterface.setImports(existingFile.getImports())

			NodeList<AnnotationExpr> interfaceAnnotation =
				NodeList.<AnnotationExpr> nodeList(existingType.annotations.findAll({ ae -> ae.name.asString() == 'Path' }))

			existingType.setAnnotations(NodeList.<AnnotationExpr> nodeList(existingType.annotations.findAll({it -> !interfaceAnnotation.contains(it)})));

			String newInterfaceName = existingType.name.asString() + "Client";

			String existingTypeName = existingType.name.asString()
			if (translation.suffixes) {
				translation.suffixes.each { key, val ->
					if (existingTypeName.endsWith(key)) {
						newInterfaceName = existingTypeName.substring(0, existingTypeName.length() - key.length());

						if (interfaceAnnotation) {
							newInterfaceName += val
						}
					}
				}
			}

			newInterfaceType = new ClassOrInterfaceDeclaration(EnumSet.of(Modifier.PUBLIC), true, newInterfaceName);

			newInterfaceType.setAnnotations(interfaceAnnotation)

			NodeList<TypeDeclaration> newInterfaceTypes = NodeList.<TypeDeclaration> nodeList([newInterfaceType] as List<TypeDeclaration>)

			newInterface.setTypes(newInterfaceTypes);

			newInterface.setImports(existingFile.getImports());
		}
	}

	public void writeNewApi(NewInterface anInterface) {
		File client = new File(destinationPackage, anInterface.newInterfaceType.name.asString() + ".java")

		client.text = anInterface.newInterface.toString()
	}

	public void parseFile(File f) {
		CompilationUnit cu;

		try {
			cu = JavaParser.parse(f);

			if (cu.getTypes() == null) {
				return;
			}

			for (TypeDeclaration td : cu.getTypes()) {
				String name = td.name.asString()
//				println td.name.asString()
				if ((!translation.includes && !translation.excludes) ||
					(translation.includes && translation.includes.findAll({ it -> name.contains(it) })) ||
					(translation.excludes && !translation.excludes.contains(td.name.asString()))
				) {
					NewInterface newInterface = extractJaxrsMethods(td, cu);

					if (newInterface != null) {
						writeNewApi(newInterface)

						f.text = cu.toString()
					}

				}

			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	private static
	final List<String> MEMBER_ANNOTATIONS = ["GET", "POST", "PUT", "DELETE", "HEAD", "Produces", "Consumes", "Path"]
	private static final List<String> METHOD_ANNOTATIONS = ["PathParam", "QueryParam", "DefaultParam"]

	private NewInterface extractJaxrsMethods(TypeDeclaration td, CompilationUnit cu) {
		NewInterface newInterface = new NewInterface(cu, td);

		println "parsing " + td.name.asString()

		if (td && td.members) {
			td.members.findAll({ BodyDeclaration body -> body && body instanceof MethodDeclaration }).each({ body ->
				addMethodToClass(body as MethodDeclaration, newInterface)
			})
		}

		if (newInterface != null) {
			if (newInterface.methodBodies) {
				newInterface.newInterfaceType.setMembers(NodeList.<BodyDeclaration> nodeList(newInterface.methodBodies))
			} else {
				newInterface = null
			}
		}

		return newInterface;
	}

	/**
	 * filter annotations on method and params.
	 *
	 * @param methodDeclaration
	 * @param newInterface
	 */
	private void addMethodToClass(MethodDeclaration methodDeclaration, NewInterface newInterface) {

		NodeList<AnnotationExpr> annotations = NodeList.<AnnotationExpr> nodeList(methodDeclaration.annotations.findAll {
			ae -> return MEMBER_ANNOTATIONS.contains(ae.name.asString())
		})

		if (annotations) { // we have at least one
			Type newMethodType = methodDeclaration.type

			if (methodDeclaration.type instanceof MethodDeclaration && ((methodDeclaration.type as MethodDeclaration).type instanceof ClassOrInterfaceType) &&
				((methodDeclaration.type as MethodDeclaration).type as ClassOrInterfaceType).name.asString().endsWith("Resource")) {
				newMethodType = new ClassOrInterfaceType(((methodDeclaration.type as MethodDeclaration).type as ClassOrInterfaceType).name.asString() - "Resource")
			}

			MethodDeclaration md = new MethodDeclaration(methodDeclaration.getModifiers(), newMethodType, methodDeclaration.name.asString());
			md.setBody(null);

			md.annotations = annotations

			// strip these annotations out
			List<AnnotationExpr> remainAnn = methodDeclaration.annotations.findAll({it -> !annotations.contains(it)})
			NodeList<AnnotationExpr> remainingAnnotations = NodeList.<AnnotationExpr> nodeList(remainAnn);

			methodDeclaration.setAnnotations(remainingAnnotations)

			// now we have to add the parameter but strip the invalid annotations
//			md.
			md.setParameters(NodeList.<Parameter> nodeList(methodDeclaration.parameters.collect({ Parameter p ->
//				imports.add(new ImportDeclaration(p.type.tokenRange))
				Parameter newParam = new Parameter(p.type, p.name)
				if (p.annotations) {
					List<AnnotationExpr> mAnnotations = p.annotations.findAll({ an ->
						METHOD_ANNOTATIONS.contains(an.name.asString())
					});
					List<AnnotationExpr> rAnnotations = p.annotations.findAll({ an -> !mAnnotations.contains(an)})
					newParam.setAnnotations(NodeList.<AnnotationExpr> nodeList(mAnnotations))
					p.setAnnotations(NodeList.<AnnotationExpr> nodeList(rAnnotations))
				}
				return newParam
			})))

			newInterface.methodBodies.add(md);
		}
	}

	void srcFiles(File files) {
		for (File f : files.listFiles()) {
			if (f.name.startsWith(".")) {
				continue;
			}

			if (f.isDirectory()) {
				srcFiles(f);
			} else if (f.name.endsWith(".java")) {
				parseFile(f);
			}
		}
	}

	public void process() {
		if (!translation.destinationPackage || !translation.destinationFolder) {
			throw new RuntimeException("Require destination package and folder.")
		}

		destinationPackage = new File(translation.destinationFolder, translation.destinationPackage.replace('.', File.separator))

		destinationPackage.mkdirs()

		srcFiles(new File(translation.sourceFolder))
	}

	public static void main(String[] args) {
		ModelReader mr = ModelReader.loadTranslations("src/test/resources/translations.yaml")

		mr.translations?.each {
			new Loader(it).process()
		}
	}
}
