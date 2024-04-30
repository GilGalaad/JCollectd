package jcollectd.engine.collector.callable;

import jcollectd.common.dto.sample.RawSample;

import java.util.concurrent.Callable;

public interface Collector extends Callable<RawSample> {
}
