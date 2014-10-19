package com.hannesdorfmann.annotatedadapter.processor.generator;

import com.hannesdorfmann.annotatedadapter.processor.AdapterInfo;
import com.hannesdorfmann.annotatedadapter.processor.FieldInfo;
import com.hannesdorfmann.annotatedadapter.processor.ViewTypeInfo;
import com.hannesdorfmann.annotatedadapter.processor.ViewTypeSearcher;
import com.hannesdorfmann.annotatedadapter.processor.util.TypeHelper;
import com.hannesdorfmann.annotatedadapter.support.recyclerview.SupportRecyclerAdapterDelegator;
import com.squareup.javawriter.JavaWriter;
import dagger.ObjectGraph;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import javax.annotation.processing.Filer;
import javax.inject.Inject;
import javax.lang.model.element.Modifier;
import javax.tools.JavaFileObject;

/**
 * @author Hannes Dorfmann
 */
public class RecyclerViewGenerator implements CodeGenerator {

  private static String VIEW_HOLDER = "android.support.v7.widget.RecyclerView.ViewHolder";

  private AdapterInfo info;
  @Inject Filer filer;
  @Inject TypeHelper typeHelper;
  private Map<String, AdapterInfo> adaptersMap;

  public RecyclerViewGenerator(ObjectGraph graph, AdapterInfo info,
      Map<String, AdapterInfo> adaptersMap) {
    this.info = info;
    this.adaptersMap = adaptersMap;
    graph.inject(this);
  }

  private void generateAdapterHelper() throws IOException {

    String packageName = typeHelper.getPackageName(info.getAdapterClass());
    String delegatorClassName = info.getAdapterDelegatorClassName();
    String delegatorBinderName = packageName + "." + delegatorClassName;

    //
    // Write code
    //

    JavaFileObject jfo = filer.createSourceFile(delegatorBinderName, info.getAdapterClass());
    Writer writer = jfo.openWriter();
    JavaWriter jw = new JavaWriter(writer);

    jw.emitPackage(packageName);
    jw.emitEmptyLine();
    jw.emitJavadoc("Generated class by AnnotatedAdapter . Do not modify this code!");

    String superAnnotatedAapterDelegatorClassName = null;
    AdapterInfo superAnnotatedAdaper = info.getAnnotatedAdapterSuperClass(adaptersMap);
    if (superAnnotatedAdaper != null) {
      superAnnotatedAapterDelegatorClassName =
          superAnnotatedAdaper.getQualifiedAdapterDelegatorClassName();
    }

    jw.beginType(delegatorClassName, "class", EnumSet.of(Modifier.PUBLIC),
        superAnnotatedAapterDelegatorClassName,
        SupportRecyclerAdapterDelegator.class.getCanonicalName());
    jw.emitEmptyLine();
    jw.emitEmptyLine();

    // Check binder interface implemented
    jw.beginMethod("void", "checkBinderInterfaceImplemented", EnumSet.of(Modifier.PUBLIC),
        ViewTypeSearcher.SUPPORT_RECYCLER_ADAPTER, "adapter");
    jw.emitEmptyLine();
    jw.beginControlFlow("if (!(adapter instanceof %s)) ", info.getBinderClassName());
    jw.emitStatement(
        "throw new java.lang.RuntimeException(\"The adapter class %s must implement the binder interface %s \")",
        info.getAdapterClassName(), info.getBinderClassName());
    jw.endControlFlow();
    jw.endMethod();

    // ViewTypeCount
    jw.beginMethod("int", "getViewTypeCount", EnumSet.of(Modifier.PUBLIC));
    if (info.hasAnnotatedAdapterSuperClass(adaptersMap)) {
      jw.emitStatement("return super.getViewTypeCount() + %d", info.getViewTypes().size());
    } else {
      jw.emitStatement("return %d", info.getViewTypes().size());
    }
    jw.endMethod();

    jw.emitEmptyLine();
    jw.emitEmptyLine();

    // onCreateViewHolder
    jw.beginMethod(VIEW_HOLDER, "onCreateViewHolder", EnumSet.of(Modifier.PUBLIC),
        ViewTypeSearcher.SUPPORT_RECYCLER_ADAPTER, "adapter", "android.view.ViewGroup", "parent",
        "int", "viewType");

    jw.emitEmptyLine();
    jw.emitStatement("%s ad = (%s) adapter", info.getAdapterClass(), info.getAdapterClass());
    jw.emitEmptyLine();

    int ifs = 0;

    for (ViewTypeInfo vt : info.getViewTypes()) {
      jw.beginControlFlow((ifs > 0 ? "else " : "") + "if (viewType == ad.%s)", vt.getFieldName());
      jw.emitStatement("android.view.View view = ad.getInflater().inflate(%d, parent, false)",
          vt.getLayoutRes());
      jw.emitStatement("%s.%s vh = new %s.%s(view)", info.getViewHoldersClassName(),
          vt.getViewHolderClassName(), info.getViewHoldersClassName(), vt.getViewHolderClassName());
      if (vt.hasViewHolderInitMethod()) {
        jw.emitStatement("%s binder = (%s) adapter", info.getBinderClassName(),
            info.getBinderClassName());
        jw.emitStatement("binder.%s(vh, view, parent)", vt.getInitMethodName());
      }
      jw.emitStatement("return vh");
      jw.endControlFlow();
      jw.emitEmptyLine();
      ifs++;
    }

    if (info.hasAnnotatedAdapterSuperClass(adaptersMap)) {
      jw.emitStatement("return super.onCreateViewHolder(adapter, parent, viewType)");
    } else {
      jw.emitStatement(
          "throw new java.lang.IllegalArgumentException(\"Unknown view type \"+viewType)");
    }
    jw.endMethod();

    jw.emitEmptyLine();
    jw.emitEmptyLine();

    // onBindViewHolder
    jw.beginMethod("void", "onBindViewHolder", EnumSet.of(Modifier.PUBLIC),
        ViewTypeSearcher.SUPPORT_RECYCLER_ADAPTER, "adapter", VIEW_HOLDER, "vh", "int", "position");

    jw.emitEmptyLine();
    jw.emitStatement("%s binder = (%s) adapter", info.getBinderClassName(),
        info.getBinderClassName());

    ifs = 0;
    for (ViewTypeInfo vt : info.getViewTypes()) {
      jw.beginControlFlow((ifs > 0 ? "else " : "") + "if (vh instanceof %s.%s)",
          info.getViewHoldersClassName(), vt.getViewHolderClassName());

      StringBuilder builder = new StringBuilder("binder.");
      builder.append(vt.getBinderMethodName());
      builder.append("( (");
      builder.append(info.getViewHoldersClassName());
      builder.append(".");
      builder.append(vt.getViewHolderClassName());
      builder.append(") vh, position");
     /*
      if (vt.hasModelClass()) {
        builder.append(", (");
        builder.append(vt.getQualifiedModelClass());
        builder.append(") getItem(position)");
      }
      */
      builder.append(")");

      jw.emitStatement(builder.toString());
      jw.emitStatement("return");
      jw.endControlFlow();
      ifs++;
    }

    if (info.hasAnnotatedAdapterSuperClass(adaptersMap)) {
      jw.emitStatement("super.onBindViewHolder(adapter, vh, position)");
    } else {
      jw.emitStatement("throw new java.lang.IllegalArgumentException("
          + "\"Binder method not found for unknown viewholder class \" + vh.toString())");
    }
    jw.endMethod();

    jw.endType();
    jw.close();
  }

  private void generateBinderInterface() throws IOException {

    String packageName = typeHelper.getPackageName(info.getAdapterClass());
    String binderClassName = info.getBinderClassName();
    String qualifiedBinderName = packageName + "." + binderClassName;

    //
    // Write code
    //

    JavaFileObject jfo = filer.createSourceFile(qualifiedBinderName, info.getAdapterClass());
    Writer writer = jfo.openWriter();
    JavaWriter jw = new JavaWriter(writer);

    jw.emitPackage(packageName);

    jw.emitStaticImports(info.getQualifiedViewHoldersClassName() + ".*");

    jw.emitJavadoc("Generated class by AnnotatedAdapter . Do not modify this code!");

    AdapterInfo superAnnotatedAdapter = info.getAnnotatedAdapterSuperClass(adaptersMap);
    String qualifiedSuperAnnotateAdapterName = null;
    if (superAnnotatedAdapter != null) {
      qualifiedSuperAnnotateAdapterName = superAnnotatedAdapter.getQualifiedBinderClassName();
    }
    jw.beginType(binderClassName, "interface", EnumSet.of(Modifier.PUBLIC),
        qualifiedSuperAnnotateAdapterName);
    jw.emitEmptyLine();

    for (ViewTypeInfo vt : info.getViewTypes()) {

      if (vt.hasViewHolderInitMethod()) {
        // Generate the init method
        jw.emitEmptyLine();
        jw.beginMethod("void", vt.getInitMethodName(), EnumSet.of(Modifier.PUBLIC),
            vt.getViewHolderClassName(), "vh", "android.view.View", "view",
            "android.view.ViewGroup", "parent");
        jw.endMethod();
      }

      jw.emitEmptyLine();
      List<String> params = new ArrayList(6);

      params.add(vt.getViewHolderClassName());
      params.add("vh");

      params.add("int");
      params.add("position");
/*
      if (vt.hasModelClass()) {
        params.add(vt.getQualifiedModelClass());
        params.add("model");
      }
*/
      jw.beginMethod("void", vt.getBinderMethodName(), EnumSet.of(Modifier.PUBLIC), params, null);

      jw.endMethod();
      jw.emitEmptyLine();
    }

    jw.endType();
    jw.close();
  }

  private void generateViewHolders() throws IOException {

    String packageName = typeHelper.getPackageName(info.getAdapterClass());
    String holdersClassName = info.getViewHoldersClassName();
    String qualifiedHoldersName = packageName + "." + holdersClassName;

    //
    // Write code
    //

    JavaFileObject jfo = filer.createSourceFile(qualifiedHoldersName, info.getAdapterClass());
    Writer writer = jfo.openWriter();
    JavaWriter jw = new JavaWriter(writer);

    // Class things
    jw.emitPackage(packageName);
    jw.emitJavadoc("Generated class by AnnotatedAdapter . Do not modify this code!");
    jw.beginType(holdersClassName, "class", EnumSet.of(Modifier.PUBLIC));
    jw.emitEmptyLine();

    jw.beginConstructor(EnumSet.of(Modifier.PRIVATE));
    jw.endConstructor();

    jw.emitEmptyLine();
    jw.emitEmptyLine();

    for (ViewTypeInfo v : info.getViewTypes()) {
      jw.beginType(v.getViewHolderClassName(), "class",
          EnumSet.of(Modifier.PUBLIC, Modifier.STATIC), VIEW_HOLDER);
      jw.emitEmptyLine();

      // Insert fields
      for (FieldInfo f : v.getFields()) {
        jw.emitField(f.getQualifiedClassName(), f.getFieldName(), EnumSet.of(Modifier.PUBLIC));
      }

      if (!v.getFields().isEmpty()) {
        jw.emitEmptyLine();
      }

      jw.beginConstructor(EnumSet.of(Modifier.PUBLIC), "android.view.View", "view");
      jw.emitEmptyLine();
      jw.emitStatement("super(view)");
      jw.emitEmptyLine();
      for (FieldInfo f : v.getFields()) {
        jw.emitStatement("%s = (%s) view.findViewById(%d)", f.getFieldName(),
            f.getQualifiedClassName(), f.getId());
      }
      jw.endConstructor();
      jw.endType();

      jw.emitEmptyLine();
      jw.emitEmptyLine();
    }

    jw.endType(); // End of holders class
    jw.close();
  }

  @Override public void generateCode() throws IOException {
    generateViewHolders();
    generateBinderInterface();
    generateAdapterHelper();
  }
}
