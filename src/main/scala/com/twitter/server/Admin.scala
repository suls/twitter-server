package com.twitter.server

import com.twitter.app.App
import com.twitter.server.handler._
import com.twitter.server.view._

object Admin {

  /**
   * Common constants for [[AdminHttpServer.Route]]'s `group`.
   */
  object Grouping {
    val ProcessInfo = "Process Info"
    val PerfProfile = "Performance Profile"
    val Utilities = "Utilities"
    val Metrics = "Metrics"
  }

}

/**
 * Defines many of the default `/admin/` HTTP routes.
 */
trait Admin { self: App with AdminHttpServer =>
  import AdminHttpServer.Route
  import Admin.Grouping

  override protected def routes: Seq[Route] = Seq(
    Route(
      path = "/admin", handler = new SummaryHandler,
      alias = "Summary", group = None, includeInIndex = true),
    Route(
      path = "/admin/server_info", handler = new TextBlockView andThen new ServerInfoHandler(self),
      alias = "Build Properties", group = Some(Grouping.ProcessInfo), includeInIndex = true),
    Route(
      path = "/admin/contention", handler = new TextBlockView andThen new ContentionHandler,
      alias = "Contention", group = Some(Grouping.ProcessInfo), includeInIndex = true),
    Route(
      path = "/admin/lint", handler = new LintHandler(),
      alias = "Lint", group = Some(Grouping.ProcessInfo), includeInIndex = true),
    Route(
      path = "/admin/lint.json", handler = new LintHandler(),
      alias = "Lint", group = Some(Grouping.ProcessInfo), includeInIndex = false),
    Route(
      path = "/admin/threads", handler = new ThreadsHandler,
      alias = "Threads", group = Some(Grouping.ProcessInfo), includeInIndex = true),
    Route(
      path = "/admin/threads.json", handler = new ThreadsHandler,
      alias = "Threads", group = Some(Grouping.ProcessInfo), includeInIndex = false),
    Route(
      path = "/admin/announcer", handler = new TextBlockView andThen new AnnouncerHandler,
      alias = "Announcer", group = Some(Grouping.ProcessInfo), includeInIndex = true),
    Route(
      path = "/admin/dtab", handler = new TextBlockView andThen new DtabHandler,
      alias = "Dtab", group = Some(Grouping.ProcessInfo), includeInIndex = true),
    Route(
      path = "/admin/pprof/heap", handler = new HeapResourceHandler,
      alias = "Heap", group = Some(Grouping.PerfProfile), includeInIndex = true),
    Route(
      path = "/admin/pprof/profile", handler = new ProfileResourceHandler(Thread.State.RUNNABLE),
      alias = "Profile", group = Some(Grouping.PerfProfile), includeInIndex = true),
    Route(
      path = "/admin/pprof/contention", handler = new ProfileResourceHandler(Thread.State.BLOCKED),
      alias = "Contention", group = Some(Grouping.PerfProfile), includeInIndex = true),
    Route(
      path = "/admin/ping", handler = new ReplyHandler("pong"),
      alias = "Ping", group = Some(Grouping.Utilities), includeInIndex = true),
    Route(
      path = "/admin/shutdown", handler = new ShutdownHandler(this),
      alias = "Shutdown", group = Some(Grouping.Utilities), includeInIndex = true),
    Route(
      path = "/admin/tracing", handler = new TracingHandler,
      alias = "Tracing", group = Some(Grouping.Utilities), includeInIndex = true),
    Route(
      path = "/admin/events", handler = new EventsHandler,
      alias = "Events", group = Some(Grouping.Utilities), includeInIndex = true),
    Route(
      path = "/admin/events/", handler = new EventRecordingHandler(),
      alias = "EventRecording", group = None, includeInIndex = false),
    Route(
      path = "/admin/logging", handler = new LoggingHandler,
      alias = "Logging", group = Some(Grouping.Utilities), includeInIndex = true),
    Route(
      path = "/admin/metrics", handler = new MetricQueryHandler,
      alias = "Watch", group = Some(Grouping.Metrics), includeInIndex = true),
    Route(
      path = "/admin/clients/", handler = new ClientRegistryHandler,
      alias = "Clients", group = None, includeInIndex = false),
    Route(
      path = "/admin/servers/", handler = new ServerRegistryHandler,
      alias = "Servers", group = None, includeInIndex = false),
    Route(
      path = "/admin/files/",
      handler = ResourceHandler.fromJar(
        baseRequestPath = "/admin/files/",
        baseResourcePath = "twitter-server"),
      alias = "Files", group = None, includeInIndex = false),
    Route(
      path = "/admin/registry.json", handler = new RegistryHandler,
      alias = "Registry", group = Some(Grouping.ProcessInfo), includeInIndex = true)
  )
}
