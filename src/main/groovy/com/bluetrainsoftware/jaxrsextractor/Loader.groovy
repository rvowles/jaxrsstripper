package com.bluetrainsoftware.jaxrsextractor

import com.bluetrainsoftware.jaxrsextractor.models.ModelReader
import com.bluetrainsoftware.jaxrsextractor.models.Translation
import com.github.javaparser.JavaParser
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.ImportDeclaration
import com.github.javaparser.ast.Modifier
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.body.BodyDeclaration
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.FieldDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.body.Parameter
import com.github.javaparser.ast.body.TypeDeclaration
import com.github.javaparser.ast.body.VariableDeclarator
import com.github.javaparser.ast.expr.AnnotationExpr
import com.github.javaparser.ast.expr.MarkerAnnotationExpr
import com.github.javaparser.ast.expr.Name
import com.github.javaparser.ast.expr.SimpleName
import com.github.javaparser.ast.type.ClassOrInterfaceType
import com.github.javaparser.ast.type.Type
import com.github.javaparser.printer.PrettyPrinter
import com.github.javaparser.printer.PrettyPrinterConfiguration
import groovy.transform.CompileStatic

@CompileStatic
public class Loader {

	private final Translation translation
	private File destinationPackage
	private PrettyPrinter prettyPrinter;

	public Loader(Translation translation) {
		this.translation = translation

		PrettyPrinterConfiguration ppc = new PrettyPrinterConfiguration()
		ppc.setIndent("\t")
		prettyPrinter = new PrettyPrinter(ppc)
	}

	List<Resource> resources = []

	public void writeNewApi(NewInterface anInterface) {
		String pkg = anInterface.newInterface.getPackageDeclaration().get().name;
		File pkgFile = new File(destinationPackage, pkg.replace('.', '/'))
		pkgFile.mkdirs()
		File client = new File(pkgFile, anInterface.newInterfaceType.name.asString() + ".java")

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
				if (td instanceof ClassOrInterfaceDeclaration) {
					ClassOrInterfaceDeclaration ctd = ClassOrInterfaceDeclaration.class.cast(td)
					String name = ctd.name.asString()
//				println td.name.asString()
					if ((!translation.includes && !translation.excludes) ||
						(translation.includes && translation.includes.findAll({ it -> name.contains(it) })) ||
						(translation.excludes && !translation.excludes.contains(ctd.name.asString()))
					) {
						NewInterface newInterface = extractJaxrsMethods(ctd, cu);

						if (newInterface != null) {
							resources.add(new Resource(newInterface: newInterface, originalFile: f, originalCompilationUnit: cu, originalType: ctd ))
						}

					}
				}
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	// ensure that our methods don't return original classes if they were mapped to interfaces
	private void checkInterfaceTypes() {
		Map<String, ImportDeclaration> swap = [:]

		// first name them
		resources.each { Resource r ->
			properlyNameInterface(r.originalType, r.newInterface.newInterfaceType)
		}

		// now create a bunch of correct Import statements to swap stuff around (which comes next)
		resources.each { Resource r ->
			r.originalFullName = r.originalCompilationUnit.packageDeclaration.get().name.toString() + "." + r.originalType.name.asString()
			r.newFullName = r.newInterface.newInterface.packageDeclaration.get().name.toString() + "." + r.newInterface.newInterfaceType.name.asString()

			println "mapping ${r.originalFullName} --> ${r.newFullName}"

			swap[r.originalFullName] = new ImportDeclaration(new Name(r.newFullName), false, false)
		}

		Map<String, String> simpleNameSwap = [:]

		resources.each { Resource r ->
			simpleNameSwap[r.originalType.name.asString()] = r.newInterface.newInterfaceType.name.asString()
		}

		// change imports from old classes to new interfaces
		resources.each { Resource r ->
			changeImportsOfClassesForInterfaces(r.newInterface.newInterface, simpleNameSwap, swap)
			changeImportsOfClassesForInterfaces(r.originalCompilationUnit, simpleNameSwap, swap)
		}

		// change the method return types in the new interface and old class
		resources.each { Resource r ->
			changeMethodReturnTypeClassesForInterfaces(r.newInterface.newInterfaceType, simpleNameSwap)
			changeMethodReturnTypeClassesForInterfaces(r.originalType, simpleNameSwap)
			changeMethodParamsClassesForInterfaces(r.originalType, simpleNameSwap)
			changeFieldClassesForInterfaces(r.originalType, simpleNameSwap)
		}

		// now we need to add in the implements interface to the old class
		resources.each { Resource r ->
			// safe to use the simple name as it is in the same package (different artifact hopefully)
			r.originalType.implementedTypes.add(new ClassOrInterfaceType(toSimpleName(r.newFullName)))

		}

		// and now save them both out
		resources.each { Resource r ->
			r.originalFile.text = prettyPrinter.print(r.originalCompilationUnit)
			writeNewApi(r.newInterface)
		}
	}

	private void properlyNameInterface(ClassOrInterfaceDeclaration existingType, ClassOrInterfaceDeclaration newType) {
		String newInterfaceName = existingType.name.asString() + "Service";

		String existingTypeName = existingType.name.asString()
		if (translation.suffixes) {
			translation.suffixes.each { key, val ->
				if (existingTypeName.endsWith(key)) {
					newInterfaceName = existingTypeName.substring(0, existingTypeName.length() - key.length());

					newInterfaceName += val
				}
			}
		}

		newType.setName(new SimpleName(newInterfaceName))
	}

	private void changeFieldClassesForInterfaces(ClassOrInterfaceDeclaration cd, Map<String, String> simpleNameSwap) {
		cd.fields.each { FieldDeclaration fd ->
			fd.variables?.each { VariableDeclarator vd ->
				if (vd.type instanceof ClassOrInterfaceType) {
					ClassOrInterfaceType ct = ClassOrInterfaceType.class.cast(vd.type)

					String swapName = simpleNameSwap[ct.name.asString()]
					if (swapName) {
						vd.type = new ClassOrInterfaceType(swapName)
					}
				}
			}
		}
	}

	private void changeMethodParamsClassesForInterfaces(ClassOrInterfaceDeclaration cd, Map<String, String> simpleNameSwap) {
		cd.methods.each { MethodDeclaration md ->
			md.parameters.each { Parameter p ->
				if (p.type instanceof ClassOrInterfaceType) {
					ClassOrInterfaceType ct = ClassOrInterfaceType.class.cast(p.type)

					String swapName = simpleNameSwap[ct.name.asString()]
					if (swapName) {
						p.type = new ClassOrInterfaceType(swapName)
					}
				}
			}
		}
	}

	// go through the methods of each resource and swap out the classes on the method if that makes sense, jax-rs often
	// returns sub-resources
	private void changeMethodReturnTypeClassesForInterfaces(ClassOrInterfaceDeclaration cd, Map<String, String> simpleNameSwap) {
		cd.methods.each { MethodDeclaration md ->
			if (md.type instanceof ClassOrInterfaceType) {
				ClassOrInterfaceType ct = ClassOrInterfaceType.class.cast(md.type)

				String swapName = simpleNameSwap[ct.name.asString()]
				if (swapName) {
					md.type = new ClassOrInterfaceType(swapName)
				}
			}
		}
	}

	private void changeImportsOfClassesForInterfaces(CompilationUnit cu, Map<String, String> simpleNameSwap, Map<String, ImportDeclaration> swap) {
		NodeList<ImportDeclaration> removeImportDeclarations = []
		NodeList<ImportDeclaration> addImportDeclarations = []

		cu.imports.each { ImportDeclaration id ->
			String oldImportPackage = id.name.asString()

			ImportDeclaration newImport = swap[oldImportPackage]

			if (newImport) {
				removeImportDeclarations.add(id)
				addImportDeclarations.add(newImport)
				simpleNameSwap[toSimpleName(id.name.asString())] = toSimpleName(toSimpleName(newImport.name.asString()))
			}
		}

		cu.imports.removeAll(removeImportDeclarations)
		cu.imports.addAll(addImportDeclarations)
	}

	private String toSimpleName(String pkg) {
		int ch = pkg.lastIndexOf('.')
		if (ch == -1) {
			return pkg
		} else {
			return pkg.substring(ch + 1)
		}
	}


	private static
	final List<String> MEMBER_ANNOTATIONS = ["GET", "POST", "PUT", "DELETE", "HEAD", "Produces", "Consumes", "Path"]
	private static final List<String> METHOD_ANNOTATIONS = ["PathParam", "QueryParam", "DefaultParam"]

	private NewInterface extractJaxrsMethods(ClassOrInterfaceDeclaration existingClass, CompilationUnit cu) {
		NewInterface newInterface = new NewInterface(cu, existingClass);

		println "parsing " + existingClass.name.asString()

		if (existingClass && existingClass.members) {
			existingClass.members.findAll({ BodyDeclaration body -> body && body instanceof MethodDeclaration }).each({ body ->
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

			// safe to add @override this point to original
			methodDeclaration.annotations.add(new MarkerAnnotationExpr(new Name("Override")))

			EnumSet<Modifier> modifiers = EnumSet.<Modifier>copyOf(methodDeclaration.getModifiers())
			modifiers.remove(Modifier.PUBLIC)
			MethodDeclaration md = new MethodDeclaration(modifiers, newMethodType, methodDeclaration.name.asString());
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
		if (!translation.destinationFolder) {
			throw new RuntimeException("Require destination package and folder.")
		}

		destinationPackage = new File(translation.destinationFolder)

		destinationPackage.mkdirs()

		srcFiles(new File(translation.sourceFolder))

		checkInterfaceTypes()
	}

	public static void main(String[] args) {
		ModelReader mr = ModelReader.loadTranslations("src/test/resources/translations.yaml")

		mr.translations?.each {
			new Loader(it).process()
		}
	}
}
