package com.adobs.ide.presentation.explorer;

import com.adobs.ide.core.monetization.IAdManager;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class ExplorerActivity_MembersInjector implements MembersInjector<ExplorerActivity> {
  private final Provider<IAdManager> adManagerProvider;

  public ExplorerActivity_MembersInjector(Provider<IAdManager> adManagerProvider) {
    this.adManagerProvider = adManagerProvider;
  }

  public static MembersInjector<ExplorerActivity> create(Provider<IAdManager> adManagerProvider) {
    return new ExplorerActivity_MembersInjector(adManagerProvider);
  }

  @Override
  public void injectMembers(ExplorerActivity instance) {
    injectAdManager(instance, adManagerProvider.get());
  }

  @InjectedFieldSignature("com.adobs.ide.presentation.explorer.ExplorerActivity.adManager")
  public static void injectAdManager(ExplorerActivity instance, IAdManager adManager) {
    instance.adManager = adManager;
  }
}
