package jcollectd.engine.collector.callable;

import jcollectd.common.dto.sample.RawSample;

import java.util.concurrent.Callable;

public abstract class Collector implements Callable<RawSample> {
}
