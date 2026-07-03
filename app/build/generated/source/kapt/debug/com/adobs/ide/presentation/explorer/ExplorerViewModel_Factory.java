package com.adobs.ide.presentation.explorer;

import com.adobs.ide.core.storage.IFileEngine;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
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
public final class ExplorerViewModel_Factory implements Factory<ExplorerViewModel> {
  private final Provider<IFileEngine> fileEngineProvider;

  public ExplorerViewModel_Factory(Provider<IFileEngine> fileEngineProvider) {
    this.fileEngineProvider = fileEngineProvider;
  }

  @Override
  public ExplorerViewModel get() {
    return newInstance(fileEngineProvider.get());
  }

  public static ExplorerViewModel_Factory create(Provider<IFileEngine> fileEngineProvider) {
    return new ExplorerViewModel_Factory(fileEngineProvider);
  }

  public static ExplorerViewModel newInstance(IFileEngine fileEngine) {
    return new ExplorerViewModel(fileEngine);
  }
}
