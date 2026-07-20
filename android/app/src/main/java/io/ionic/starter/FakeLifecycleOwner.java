package io.ionic.starter;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;

public class FakeLifecycleOwner implements LifecycleOwner {

  private final LifecycleRegistry lifecycle = new LifecycleRegistry(this);

  public FakeLifecycleOwner() {
    lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START);
  }

  @NonNull
  @Override
  public Lifecycle getLifecycle() {
    return lifecycle;
  }
}
