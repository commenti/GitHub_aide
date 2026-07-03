package com.adobs.ide.core.storage;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast"
})
public final class FileEngineImpl_Factory implements Factory<FileEngineImpl> {
  @Override
  public FileEngineImpl get() {
    return newInstance();
  }

  public static FileEngineImpl_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static FileEngineImpl newInstance() {
    return new FileEngineImpl();
  }

  private static final class InstanceHolder {
    private static final FileEngineImpl_Factory INSTANCE = new FileEngineImpl_Factory();
  }
}
