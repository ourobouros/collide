package com.google.collide.plugin.server.gwt;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;

import org.vertx.java.core.eventbus.EventBus;

import xapi.file.X_File;
import xapi.io.api.LineReader;
import xapi.log.X_Log;
import xapi.shell.X_Shell;
import xapi.shell.api.ShellSession;
import xapi.util.X_Debug;
import collide.shared.manifest.GwtManifest;

import com.google.collide.dto.CodeModule;
import com.google.collide.dto.GwtCompile;
import com.google.collide.dto.server.DtoServerImpls.GwtCompileImpl;
import com.google.collide.dto.server.DtoServerImpls.GwtRecompileImpl;
import com.google.collide.json.shared.JsonArray;
import com.google.collide.plugin.server.ReflectionChannelTreeLogger;
import com.google.collide.shared.util.JsonCollections;
import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.TreeLogger.Type;
import com.google.gwt.core.ext.UnableToCompleteException;
import com.google.gwt.dev.Compiler;
import com.google.gwt.dev.codeserver.GwtCompilerThread;
import com.google.gwt.dev.util.log.PrintWriterTreeLogger;

public class GwtCompiler {

  private Object compiler;
  private JsonArray<String> src = JsonCollections.createArray();
  private JsonArray<String> deps = JsonCollections.createArray();
  private final String module;
  private CrossThreadVertxChannel io;
  private UrlAndSystemClassLoader cl;
  private TreeLogger log;
  private Method compileMethod;
  
  
  public GwtCompiler(String module) {
    this.module = module;
    log = new PrintWriterTreeLogger();
  }

  public boolean isRunning() {
    if (compiler == null) {
      return false;
    }
    try {
      return (Boolean)compiler.getClass().getMethod("isRunning").invoke(compiler);
    } catch (Exception e) {
      throw X_Debug.rethrow(e);
    }
  }

  public boolean isStarted() {
    if (compiler == null) {
      return false;
    }
    try {
      return (Boolean)compiler.getClass().getMethod("isStarted").invoke(compiler);
    } catch (Exception e) {
      throw X_Debug.rethrow(e);
    }
  }

  public void kill() {
    if (compiler != null) {
      try {
        Class<?> cls = compiler.getClass();
        cls.getMethod("kill").invoke(compiler);
        cls.getMethod("interrupt").invoke(compiler);
        Thread.sleep(100);
        if (isRunning()) {
          System.err.println("Module "+module+" did not shut down nicely; forcing a kill.\n"
              + "Beware that we are using Thread.stop(); if you experience deadlock, "
              + "you may need to restart the server.");
          ClassLoader cl = cls.getClassLoader();
          Object ex = cl.loadClass(UnableToCompleteException.class.getName()).newInstance();
          cls.getMethod("stop", Throwable.class).invoke(compiler, ex);
        }
      } catch (Exception e) {
        throw X_Debug.rethrow(e);
      }
      compiler = null;
    }
  }

  public boolean isMatchingClasspath(CodeModule code) {
    return matches(src, code.getSources()) && matches(deps, code.getSources());
  }

  private boolean matches(JsonArray<String> one, JsonArray<String> two) {
    return JsonCollections.equals(one, two);
  }

  public void scheduleRecompile() {
    if (compiler != null) {
      try {
        compiler.getClass().getMethod("doRecompile").invoke(compiler);
      } catch (Exception e) {
        throw X_Debug.rethrow(e);
      }
    }
  }

  public void scheduleCompile() {
    if (compiler != null) {
      try {
        compiler.getClass().getMethod("doCompile").invoke(compiler);
      } catch (Exception e) {
        throw X_Debug.rethrow(e);
      }
    }
  }

  public void recompile(String request) {
    assert compiler != null : "You must initailize the internal compiler before calling .compile() on "+getClass().getName();
    io.setOutput(request);
    try {
      compileMethod.invoke(compiler, request);
    } catch (Throwable e) {
      throw X_Debug.rethrow(e);
    }
  }

  public void initialize(GwtRecompileImpl compileRequest, URL[] cp, EventBus eb, String address) {
    if (cl != null) {
      if (!Arrays.equals(cp, cl.getURLs())) {
        cl = null;
      }
    }
    if (cl == null) {
      cl = new UrlAndSystemClassLoader(cp, log);
    } else {
      cl.setAllowSystem(true);
    }
    if (io == null) {
      io = new CrossThreadVertxChannel(cl, eb, address) {
        @Override
        public void destroy() throws Exception {
          io = null;
          compiler = null;
          super.destroy();
        }
      };
    }
    try {
      Class<?> recompilerClass = cl.loadClass(GwtCompilerThread.class.getName());
      Class<?> stringClass = cl.loadClass(String.class.getName());
      Class<?> classLoaderClass = cl.loadClass(ClassLoader.class.getName());
      Class<?> objectClass = cl.loadClass(Object.class.getName());
      Constructor<?> ctor = recompilerClass.getConstructor(stringClass);
      compiler = ctor.newInstance(module);
      Method method = recompilerClass.getMethod("setContextClassLoader", classLoaderClass);
      method.invoke(compiler, cl);

      method = recompilerClass.getMethod("setDaemon", boolean.class);
      method.invoke(compiler, true);
      method = recompilerClass.getMethod("setChannel", classLoaderClass, objectClass);
      method.invoke(compiler, getClass().getClassLoader(), io);
      io.setChannel(null);
      log = new ReflectionChannelTreeLogger(io);
      log.log(Type.INFO, "Initialized GWT recompiler for "+module);
      compileMethod = recompilerClass.getMethod("compile", String.class);
      cl.setAllowSystem(false);
    } catch (Exception e) {
      log.log(Type.ERROR, "Unable to start the GWT compiler", e);
    }
  }

  public CrossThreadVertxChannel getIO() {
    assert io != null : "You must call .initialize() before calling getIO() in "+getClass().getName();
    return io;
  }

  public void log(String item) {
    log.log(Type.TRACE, item);
  }

  public void compile(GwtCompileImpl compileRequest) {
    if (compileRequest.getWarDir() == null) {
      File f = X_File.createTempDir("gwtc-"+compileRequest.getModule());
      if (f != null) {
        try {
          compileRequest.setWarDir(f.getCanonicalPath());
        } catch (IOException e) {
          X_Log.warn("Unable to create temporary war directory for GWT compile",
              "You will likely get an unwanted war folder in the directory you executed this program");
          X_Debug.maybeRethrow(e);
        }
      }
    }
    if (compileRequest.getUnitCacheDir() == null) {
      try {
        File f = X_File.createTempDir("gwtc-"+compileRequest.getModule()+"UnitCache");
        if (f != null) {
          compileRequest.setUnitCacheDir(f.getCanonicalPath());
        }
      } catch (IOException e) {
        X_Log.warn("Unable to create unit cache work directory for GWT compile",
            "You will likely get unwanted gwtUnitcache folders in the directory you executed this program");
      }
    }
    
    GwtManifest manifest = new GwtManifest(compileRequest);
    X_Log.info(getClass(), "Starting gwt compile", compileRequest.getModule());
    X_Log.trace(compileRequest);
    X_Log.info("Args: java ", manifest.toJvmArgs(),manifest.toProgramArgs());
    X_Log.info("Requested Classpath\n",manifest.toClasspathFullCompile("lib"));
    X_Log.debug("Runtime cp", ((URLClassLoader)getClass().getClassLoader()).getURLs());
    ShellSession controller 
      = X_Shell.launchJava(Compiler.class, manifest.toClasspathFullCompile("lib"), manifest.toJvmArgArray(), manifest.toProgramArgs().split("[ ]+"));
    controller.stdErr(new LineReader() {
      @Override
      public void onStart() {
      }
      
      @Override
      public void onLine(String errLog) {
        if (!errLog.matches("\\s*[ERROR]")) {
          errLog = "[ERROR] "+errLog;
        }
        System.err.println(errLog);
        log(errLog);
      }
      
      @Override
      public void onEnd() {
      }
    });
    controller.stdOut(new LineReader() {
      @Override
      public void onStart() {
      }
      @Override
      public void onLine(String logLine) {
        System.out.println(logLine);
        log(logLine);
      }
      @Override
      public void onEnd() {
        
      }
    });
  }

}
