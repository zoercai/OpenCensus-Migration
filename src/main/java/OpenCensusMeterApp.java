import com.google.common.collect.Lists;
import io.opencensus.exporter.stats.stackdriver.StackdriverStatsExporter;
import io.opencensus.stats.Aggregation;
import io.opencensus.stats.BucketBoundaries;
import io.opencensus.stats.Measure.MeasureLong;
import io.opencensus.stats.Stats;
import io.opencensus.stats.StatsRecorder;
import io.opencensus.stats.View;
import io.opencensus.stats.View.Name;
import io.opencensus.stats.ViewManager;
import java.io.IOException;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class OpenCensusMeterApp {
  private static final int EXPORT_INTERVAL = 70;
  private static final MeasureLong LATENCY_MS =
      MeasureLong.create("task_latency", "The task latency in milliseconds", "ms");
  // Latency in buckets:
  // [>=0ms, >=100ms, >=200ms, >=400ms, >=1s, >=2s, >=4s]
  private static final BucketBoundaries LATENCY_BOUNDARIES =
      BucketBoundaries.create(Lists.newArrayList(0d, 100d, 200d, 400d, 1000d, 2000d, 4000d));
  private static final StatsRecorder STATS_RECORDER = Stats.getStatsRecorder();

  public static void main(String[] args) throws IOException, InterruptedException {
    // Register the view. It is imperative that this step exists,
    // otherwise recorded metrics will be dropped and never exported.
    View view =
        View.create(
            Name.create("task_latency_distribution"),
            "The distribution of the task latencies.",
            LATENCY_MS,
            Aggregation.Distribution.create(LATENCY_BOUNDARIES),
            Collections.emptyList());

    ViewManager viewManager = Stats.getViewManager();
    viewManager.registerView(view);

    // Enable OpenCensus exporters to export metrics to Stackdriver Monitoring.
    // Exporters use Application Default Credentials to authenticate.
    // See https://developers.google.com/identity/protocols/application-default-credentials
    // for more details.
    StackdriverStatsExporter.createAndRegister();

    // Record 100 fake latency values between 0 and 5 seconds.
    Random rand = new Random();
    for (int i = 0; i < 100; i++) {
      long ms = (long) (TimeUnit.MILLISECONDS.convert(5, TimeUnit.SECONDS) * rand.nextDouble());
      System.out.println(String.format("Latency %d: %d", i, ms));
      STATS_RECORDER.newMeasureMap().put(LATENCY_MS, ms).record();
    }

    // The default export interval is 60 seconds. The thread with the StackdriverStatsExporter must
    // live for at least the interval past any metrics that must be collected, or some risk being
    // lost if they are recorded after the last export.

    System.out.println(
        String.format(
            "Sleeping %d seconds before shutdown to ensure all records are flushed.",
            EXPORT_INTERVAL));
    Thread.sleep(TimeUnit.MILLISECONDS.convert(EXPORT_INTERVAL, TimeUnit.SECONDS));
  }
}
