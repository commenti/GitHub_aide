package com.adobs.ide.core.monetization;

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
public final class AdManagerServiceImpl_Factory implements Factory<AdManagerServiceImpl> {
  @Override
  public AdManagerServiceImpl get() {
    return newInstance();
  }

  public static AdManagerServiceImpl_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static AdManagerServiceImpl newInstance() {
    return new AdManagerServiceImpl();
  }

  private static final class InstanceHolder {
    private static final AdManagerServiceImpl_Factory INSTANCE = new AdManagerServiceImpl_Factory();
  }
}
