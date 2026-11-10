import java.nio.file.Files
import java.nio.file.StandardCopyOption

def command
def repo
def moduleTarget
def version
def jdkHome
if (binding.hasVariable('xalanPrepareCommand')) {
    command = xalanPrepareCommand
    repo = project.basedir.parentFile.canonicalFile
    moduleTarget = new File(project.build.directory).canonicalFile
    version = project.version
    def home = System.getenv('BELLSOFT_JDK8_HOME')
    if (!home) {
        throw new IllegalStateException('Set BELLSOFT_JDK8_HOME to a BellSoft Liberica JDK 8 Standard installation')
    }
    jdkHome = new File(home).canonicalFile
} else {
    command = args[0]
    repo = new File(args[1]).canonicalFile
    moduleTarget = new File(args[2]).canonicalFile
    version = args[3]
    jdkHome = new File(args[4]).canonicalFile
}
def javaBin = new File(jdkHome, 'bin/java')
def jarBin = new File(jdkHome, 'bin/jar')
def javacBin = new File(jdkHome, 'bin/javac')

if (!javacBin.isFile()) {
    throw new IllegalStateException("BellSoft javac is missing: ${javacBin}")
}

def settings = new StringBuilder()
def show = new ProcessBuilder(javaBin.absolutePath, '-XshowSettings:properties', '-version')
show.redirectErrorStream(true)
def showProc = show.start()
showProc.inputStream.eachLine { line -> settings.append(line).append('\n') }
if (showProc.waitFor() != 0) {
    throw new IllegalStateException("Could not read Java settings from ${javaBin}")
}
def text = settings.toString()
def javaVersion = (text =~ /java\.version = (.+)/)[0][1].trim()
def javaVendor = (text =~ /java\.vendor = (.+)/)[0][1].trim()
if (javaVendor != 'BellSoft' || !javaVersion.startsWith('1.8.') || !text.contains('bell-sw.com')) {
    throw new IllegalStateException("BELLSOFT_JDK8_HOME must be BellSoft Liberica JDK 8 Standard. java.vendor=${javaVendor} java.version=${javaVersion}")
}

def tokens = [
        'version.VERSION'       : '2',
        'version.RELEASE'       : '7',
        'version.MINOR'         : '3',
        'version.DEVELOPER'     : '',
        'parser.version.VERSION': '2',
        'parser.version.RELEASE': '12',
        'parser.version.MINOR'  : '2',
        'impl.version'          : version,
        'java.version'          : javaVersion,
        'java.vendor'           : javaVendor,
]

def filterFile = { File src, File dest ->
    def body = src.getText('UTF-8')
    tokens.each { name, value ->
        body = body.replace("@${name}@", value)
    }
    ['@impl.version@', '@java.version@', '@java.vendor@', '@version.VERSION@', '@version.RELEASE@', '@version.MINOR@', '@version.DEVELOPER@'].each { leftover ->
        if (body.contains(leftover)) {
            throw new IllegalStateException("Unreplaced ${leftover} in ${dest}")
        }
    }
    dest.parentFile.mkdirs()
    dest.setText(body, 'UTF-8')
}

def run = { List<String> commandLine, File dir ->
    def process = new ProcessBuilder(commandLine)
    process.directory(dir)
    process.redirectErrorStream(true)
    def proc = process.start()
    def log = proc.inputStream.getText('UTF-8')
    def code = proc.waitFor()
    if (code != 0) {
        System.err.println(log)
        throw new IllegalStateException("${commandLine[0]} exited ${code}")
    }
}

if (command == 'generate-serializer') {
    def generated = new File(moduleTarget, 'generated-sources')
    filterFile(
            new File(repo, 'src/org/apache/xml/serializer/Version.src'),
            new File(generated, 'org/apache/xml/serializer/Version.java'))
    filterFile(
            new File(repo, 'src/MANIFEST.SERIALIZER'),
            new File(moduleTarget, 'MANIFEST.MF'))
} else if (command == 'generate-xalan') {
    def generated = new File(moduleTarget, 'generated-sources')
    filterFile(
            new File(repo, 'src/org/apache/xalan/Version.src'),
            new File(generated, 'org/apache/xalan/Version.java'))
    filterFile(
            new File(repo, 'src/org/apache/xalan/processor/XSLProcessorVersion.src'),
            new File(generated, 'org/apache/xalan/processor/XSLProcessorVersion.java'))
    filterFile(
            new File(repo, 'src/org/apache/xalan/res/XSLTInfo.properties'),
            new File(moduleTarget, 'generated-resources/org/apache/xalan/res/XSLTInfo.properties'))
    filterFile(
            new File(repo, 'src/MANIFEST.MF'),
            new File(moduleTarget, 'MANIFEST.MF'))

    def compilerOut = new File(generated, 'org/apache/xalan/xsltc/compiler')
    compilerOut.mkdirs()
    run([
            javaBin.absolutePath,
            '-cp',
            new File(repo, 'tools/java_cup.jar').absolutePath + File.pathSeparator + new File(repo, 'lib/runtime.jar').absolutePath,
            'java_cup.Main',
            '-parser', 'XPathParser',
            '-expect', '0',
            '-nosummary',
            new File(repo, 'src/org/apache/xalan/xsltc/compiler/xpath.cup').absolutePath
    ], compilerOut)
    ['XPathParser.java', 'sym.java'].each { name ->
        if (!new File(compilerOut, name).isFile()) {
            throw new IllegalStateException("java_cup did not write ${name}")
        }
    }

    def lexCopy = new File(compilerOut, 'xpath.lex')
    Files.copy(
            new File(repo, 'src/org/apache/xalan/xsltc/compiler/xpath.lex').toPath(),
            lexCopy.toPath(),
            StandardCopyOption.REPLACE_EXISTING)
    run([
            javaBin.absolutePath,
            '-cp', new File(repo, 'tools/JLex.jar').absolutePath,
            'JLex.Main',
            '-static',
            lexCopy.absolutePath
    ], compilerOut)
    def produced = new File(compilerOut, 'xpath.lex.java')
    def lexer = new File(compilerOut, 'XPathLexer.java')
    if (!produced.isFile() || !produced.renameTo(lexer)) {
        throw new IllegalStateException("JLex did not write XPathLexer.java")
    }
    lexCopy.delete()

    def compileExt = new File(moduleTarget, 'compile-ext')
    compileExt.mkdirs()
    ['lib/bcel-6.7.0.jar', 'lib/runtime.jar', 'lib/regexp.jar', 'tools/java_cup.jar', 'tools/JLex.jar'].each { relative ->
        def source = new File(repo, relative)
        Files.copy(source.toPath(), new File(compileExt, source.name).toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
    def serializerJar = new File(repo, "serializer/target/serializer-${version}.jar")
    def serializerForCompile = new File(compileExt, 'serializer.jar')
    if (serializerJar.isFile()) {
        Files.copy(serializerJar.toPath(), serializerForCompile.toPath(), StandardCopyOption.REPLACE_EXISTING)
    } else {
        def classes = new File(repo, 'serializer/target/classes')
        if (!classes.isDirectory()) {
            throw new IllegalStateException('Build the serializer module before xalan')
        }
        run([jarBin.absolutePath, 'cf', serializerForCompile.absolutePath, '-C', classes.absolutePath, '.'], repo)
    }
} else if (command == 'unpack-xalan') {
    def classes = new File(moduleTarget, 'classes')
    ['lib/bcel-6.7.0.jar', 'lib/runtime.jar', 'lib/regexp.jar'].each { relative ->
        def zip = new java.util.zip.ZipFile(new File(repo, relative))
        try {
            zip.entries().each { entry ->
                if (entry.directory || entry.name == 'META-INF/MANIFEST.MF') {
                    return
                }
                def out = new File(classes, entry.name)
                out.parentFile.mkdirs()
                zip.getInputStream(entry).withCloseable { input ->
                    Files.copy(input, out.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            }
        } finally {
            zip.close()
        }
    }
} else {
    throw new IllegalArgumentException("Unknown command ${command}")
}
