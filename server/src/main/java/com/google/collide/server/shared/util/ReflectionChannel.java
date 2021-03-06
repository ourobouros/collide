package com.google.collide.server.shared.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.google.collide.shared.util.Channel;
import xapi.fu.Lazy;
import xapi.fu.lazy.ResettableLazy;

public class ReflectionChannel implements Channel<String>{

  private ClassLoader cl;
  private Object that;
  private final ResettableLazy<Method> in = new ResettableLazy<>(() -> {
      try {
        return getInputMethod(getClassLoader(), that);
      } catch (Exception e) {
        e.printStackTrace();
        throw new RuntimeException(e);
      }
  });
  private final ResettableLazy<Method> out = new ResettableLazy<>(() -> {
      try {
        return getOutputMethod(getClassLoader(), that);
      } catch (Exception e) {
        e.printStackTrace();
        throw new RuntimeException(e);
      }
  });

  private Object destroy;

  public ReflectionChannel(ClassLoader cl, Object otherChannel) {
    this.cl = cl;
    this.that = otherChannel;
  }

  public void setChannel(Object otherChannel){
    this.that = otherChannel;
    //clears our singletons, so the next invocation will rip the methods again
    in.reset();
    out.reset();
  }
  public ClassLoader getClassLoader(){
    return cl;
  }

  /**
   * @throws ClassNotFoundException
   */
  protected Method getInputMethod(ClassLoader cl, Object from) throws NoSuchMethodException, SecurityException, ClassNotFoundException {
    return from.getClass().getMethod("receive");
  }

  protected Method getOutputMethod(ClassLoader cl, Object from) throws NoSuchMethodException, SecurityException {
    return from.getClass().getMethod("send", String.class);
  }

  @Override
  public void send(String t) {
    try{
      invokeSend(out.out1(),that, t);
    }catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }


  @Override
  public String receive() {
    try{
      return invokeReceive(in.out1(), that);
    }catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  protected String invokeReceive(Method method, Object that) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
    //because this method and object are from a different classloader,
    //we can't invoke the .invoke method directly...
    Object o = method.invoke(that);
    return o == null ? null : String.valueOf(o);
  }

  protected void invokeSend(Method method, Object that, String message) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
    method.invoke(that, message);
  }

  public void setOnDestroy(Object runOnDestroy) {
    this.destroy = runOnDestroy;
  }

  public void destroy() throws Exception{
    System.out.println("DESTROYING GWT COMPILER IMPLEMENTATION");
    if (destroy != null){
      destroy.getClass().getMethod("run").invoke(destroy);
      destroy = null;
    }
  }

}
