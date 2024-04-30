package jcollectd.engine.collector.runnable;

import jcollectd.common.dto.sample.RawSample;

import java.util.concurrent.Callable;

public abstract class CollectorRunnable implements Callable<RawSample> {
}
