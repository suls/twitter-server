package com.twitter.server.view

import com.twitter.concurrent.AsyncStream
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.io.{Reader, Buf, Charsets}
import com.twitter.server.util.HttpUtils.{expectsHtml, newResponse}
import com.twitter.util.Future

object IndexView {
  sealed trait Entry
  case class Link(id: String, href: String) extends Entry
  case class Group(id: String, links: Seq[Entry]) extends Entry

  implicit object EntryOrdering extends Ordering[Entry] {
    def compare(a: Entry, b: Entry) = (a, b) match {
      case (Link(_, _), Group(_, _)) => -1
      case (Group(_, _), Link(_, _)) => 1
      case (Link(id1, _), Link(id2, _)) => id1 compare id2
      case (Group(id1, _), Group(id2, _)) => id1 compare id2
    }
  }

  /** Render nav and contents into an html template. */
  def render(title: String, uri: String, nav: Seq[Entry], contents: Reader): Reader = {

    def renderNav(
      ls: Seq[Entry],
      sb: StringBuilder = new StringBuilder
    ): String = ls match {
        case Seq() => sb.toString

        case Link(id, href) +: rest =>
          val selected = if (href == uri) "selected" else ""
          sb ++= s"""
            <li id="${id}" class="selectable $selected">
              <a href="${href}">${id}</a>
            </li>"""
          renderNav(rest, sb)

        case Group(id, links) +: rest =>
          val isChild = links exists {
            // Instead of strictly checking for href == uri,
            // we are a bit more liberal to allow for "catch-all"
            // endpoints (ex. /admin/clients/).
            case Link(_, href) => !href.stripPrefix(uri).contains("/")
            case _ => false
          }
          val active = if (isChild) "active" else ""
          sb ++= s"""
            <li class="subnav $active">
              <span class="glyphicon glyphicon-expand"></span>
              <span>${id}</span>
              <ul>${renderNav(links)}</ul>
            </li>"""
          renderNav(rest, sb)
      }

    Reader.concat(
      Reader.fromBuf(
        Buf.Utf8(
          s"""<!doctype html>
            <html>
              <head>
                <title>${title} &middot; Twitter Server Admin</title>
                <!-- css -->
                <link type="text/css" href="/admin/files/css/bootstrap.min.css" rel="stylesheet"/>
                <link type="text/css" href="/admin/files/css/index.css" rel="stylesheet"/>
                <link type="text/css" href="/admin/files/css/client-registry.css" rel="stylesheet"/>
                <!-- js -->
                <script type="application/javascript" src="//www.google.com/jsapi"></script>
                <script type="application/javascript" src="/admin/files/js/jquery.min.js"></script>
                <script type="application/javascript" src="/admin/files/js/bootstrap.min.js"></script>
                <script type="application/javascript" src="/admin/files/js/index.js"></script>
                <script type="application/javascript" src="/admin/files/js/utils.js"></script>
              </head>
              <body>
                <div id="wrapper">
                  <nav id="sidebar">
                    <ul>${renderNav(nav)}</ul>
                  </nav>
                  <div id="toggle"><span class="glyphicon glyphicon-chevron-left"></span></div>
                  <div id="contents" class="container-fluid">
                    <div class="row">
                      <div class="col-md-12">"""
        )
      )
      +:: contents
      +:: AsyncStream.of(Reader.fromBuf(
        Buf.Utf8("""</div>
                    </div>
                  </div>
                </div>
              </body>
            </html>"""))
        )
    )
  }
}

/**
 * Wraps content returned from the underlying service with a view of
 * the index. Note, the view is wrapped around the underlying service
 * selectively. For example, if it's a request from a browser and
 * the content is an html fragment.
 */
class IndexView(title: String, uri: String, index: () => Seq[IndexView.Entry])
  extends SimpleFilter[Request, Response] {
  import IndexView._

  private[this] def isFragment(contentType: String, content: String): Boolean = {
    contentType.toLowerCase.contains("text/html") && !content.contains("<html>")
  }

  def apply(req: Request, svc: Service[Request, Response]) =
    if (!expectsHtml(req)) svc(req)
    else svc(req) flatMap { res =>
      val contentType = res.headerMap.get("content-type").orNull
      val content = res.contentString
      res match {
        case _ if !isFragment(contentType, content) => Future.value(res)
        case res if res.isChunked =>
          val response = Response()
          response.contentType = "text/html;charset=UTF-8"
          response.setChunked(true)
          val reader = render(title, uri, index().sorted, res.reader)
          Reader.copy(reader, response.writer) ensure response.writer.close()
          Future.value(response)

        case res =>
          val body = Reader.fromBuf(Buf.Utf8(content))
          val reader = render(title, uri, index().sorted, body)
          Reader.readAll(reader).flatMap { html =>
            newResponse(
              contentType = "text/html;charset=UTF-8",
              content = html
            )
          }
      }
    }
}
