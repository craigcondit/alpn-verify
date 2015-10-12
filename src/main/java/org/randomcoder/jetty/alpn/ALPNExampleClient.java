package org.randomcoder.jetty.alpn;

import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.http2.api.Session;
import org.eclipse.jetty.http2.api.Stream;
import org.eclipse.jetty.http2.api.server.ServerSessionListener;
import org.eclipse.jetty.http2.client.HTTP2Client;
import org.eclipse.jetty.http2.frames.DataFrame;
import org.eclipse.jetty.http2.frames.HeadersFrame;
import org.eclipse.jetty.http2.frames.PushPromiseFrame;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.FuturePromise;
import org.eclipse.jetty.util.Jetty;
import org.eclipse.jetty.util.Promise;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;

/**
 * Run this with:
 * 
 * -Xbootclasspath/p:/home/{user}/.m2/repository/org/mortbay/jetty/alpn/alpn-boot/${alpn-boot-
 * version}/alpn-boot-${alpn-boot-version}.jar
 *
 */
public class ALPNExampleClient {

  public static void main(String[] args) throws Exception {

    HTTP2Client client = new HTTP2Client();
    SslContextFactory sslContextFactory = new SslContextFactory();
    client.addBean(sslContextFactory);
    client.start();

    String host = "nghttp2.org";
    int port = 443;

    FuturePromise<Session> sessionPromise = new FuturePromise<>();
    client.connect(sslContextFactory, new InetSocketAddress(host, port), new ServerSessionListener.Adapter(), sessionPromise);
    Session session = sessionPromise.get(5, TimeUnit.SECONDS);

    HttpFields requestFields = new HttpFields();
    requestFields.put("User-Agent", client.getClass().getName() + "/" + Jetty.VERSION);
    MetaData.Request metaData =
            new MetaData.Request("GET", new HttpURI("https://" + host + ":" + port + "/"), HttpVersion.HTTP_2, requestFields);
    HeadersFrame headersFrame = new HeadersFrame(metaData, null, true);
    final Phaser phaser = new Phaser(2);
    session.newStream(headersFrame, new Promise.Adapter<>(), new Stream.Listener.Adapter() {
      @Override
      public void onHeaders(Stream stream, HeadersFrame frame) {
        System.err.println("Headers: " + frame.getMetaData() + " (stream " + frame.getStreamId() + ")");
        if (frame.isEndStream())
          phaser.arrive();
      }

      @Override
      public void onData(Stream stream, DataFrame frame, Callback callback) {
        System.err.println("Data: " + frame + " (stream " + frame.getStreamId() + ")");
        callback.succeeded();
        if (frame.isEndStream())
          phaser.arrive();
      }

      @Override
      public Stream.Listener onPush(Stream stream, PushPromiseFrame frame) {
        System.err.println("Push: " + frame.getMetaData() + " (stream " + frame.getStreamId() + " => "
                + frame.getPromisedStreamId() + ")");
        phaser.register();
        return this;
      }
    });

    phaser.awaitAdvanceInterruptibly(phaser.arrive(), 5, TimeUnit.SECONDS);

    client.stop();

  }

}
