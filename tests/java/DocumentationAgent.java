import java.lang.instrument.Instrumentation;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
/** Renders an isolated, in-memory Oracle result table inside its native module loader. */
public final class DocumentationAgent {
    public static void agentmain(String arguments,Instrumentation instrumentation){new Thread(()->{
        String[] paths=arguments.split("\\|",2);Path output=Path.of(paths[1]);
        try{
            ClassLoader parent=null;
            for(Class<?> type:instrumentation.getAllLoadedClasses())if(type.getName().equals("oracle.dbtools.raptor.config.DBConfig")||type.getName().equals("oracle.dbtools.raptor.plsql.BackgroundParser")){parent=type.getClassLoader();break;}
            if(parent==null)throw new IllegalStateException("Open SQL Developer with a worksheet first; no database connection is needed.");
            try(URLClassLoader loader=new URLClassLoader(new java.net.URL[]{Path.of(paths[0]).toUri().toURL()},parent)){
                loader.loadClass("io.github.sqldevgrid.DocumentationOracleGrid").getMethod("render",String.class).invoke(null,output.resolve("results-50pt.png").toString());
            }
            Files.writeString(output.resolve(".oracle-render-result.txt"),"PASS real Oracle ResultSetTable rendered from in-memory documentation rows; no SQL query, editor change or desktop input.",StandardCharsets.UTF_8);
        }catch(Throwable error){java.io.StringWriter text=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(text));try{Files.writeString(output.resolve(".oracle-render-result.txt"),text.toString(),StandardCharsets.UTF_8);}catch(Exception ignored){}}
    },"configurator-documentation-render").start();}
}
