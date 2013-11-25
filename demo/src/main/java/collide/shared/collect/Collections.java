package collide.shared.collect;

import java.util.ArrayList;
import java.util.List;

import xapi.util.X_Debug;

import com.google.collide.json.client.JsoArray;
import com.google.collide.json.shared.JsonArray;
import com.google.gwt.reflect.client.GwtReflect;

import elemental.js.util.JsArrayOfString;
import elemental.util.ArrayOf;
import elemental.util.ArrayOfString;
import elemental.util.impl.JreArrayOf;
import elemental.util.impl.JreArrayOfString;

public class Collections {

  public static Iterable<String> asIterable(ArrayOfString strings) {
    return new ArrayOfStringIterable(strings);
  }

  public static <T> Iterable<T> asIterable(ArrayOf<T> objects) {
    return new ArrayOfIterable<T>(objects);
  }

  public static List<String> asList(ArrayOfString array) {
    // Always make a new copy
    if (array instanceof JreArrayOfString) {
      try {
        return asList(GwtReflect.<ArrayOf<String>>fieldGet(JreArrayOfString.class, "array", array));
      } catch (Throwable e) {
        throw X_Debug.rethrow(e);
      }
    } else {
      List<String> ret = new ArrayList<String>();
      for (String item : asIterable(array)) {
        ret.add(item);
      }
      return ret;
    }
  }
  
  public static <T> List<T> asList(ArrayOf<T> array) {
    List<T> ret = new ArrayList<T>();
    if (array instanceof JreArrayOf) {
      try {
        ret.addAll(GwtReflect.<List<T>>fieldGet(JreArrayOf.class, "array", array));
      } catch (Throwable e) {
        throw X_Debug.rethrow(e);
      }
    } else {
      for (T item : asIterable(array)) {
        ret.add(item);
      }
    }
    return ret;
  }

  public static ArrayOfString asArray(JsonArray<String> array) {
    if (array instanceof JsoArray) {
      return ((JsoArray<String>)array).<JsArrayOfString>cast();
    } else {
      ArrayOfString ret = elemental.util.Collections.arrayOfString();
      for (String value : array.asIterable()) {
        ret.push(value);
      }
      return ret;
    }
  }
  
}
