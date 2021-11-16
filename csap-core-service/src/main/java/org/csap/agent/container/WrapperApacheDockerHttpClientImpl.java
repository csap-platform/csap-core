//package org.csap.agent.container ;
//
//import java.io.IOException ;
//import java.io.InputStream ;
//import java.io.OutputStream ;
//import java.net.Socket ;
//import java.net.SocketAddress ;
//import java.net.URI ;
//import java.nio.ByteBuffer ;
//import java.nio.channels.AsynchronousByteChannel ;
//import java.nio.channels.AsynchronousCloseException ;
//import java.nio.channels.AsynchronousFileChannel ;
//import java.nio.channels.Channels ;
//import java.nio.channels.CompletionHandler ;
//import java.nio.file.FileSystemException ;
//import java.nio.file.Paths ;
//import java.nio.file.StandardOpenOption ;
//import java.util.Arrays ;
//import java.util.List ;
//import java.util.Map ;
//import java.util.concurrent.CompletableFuture ;
//import java.util.concurrent.Future ;
//import java.util.concurrent.atomic.AtomicBoolean ;
//import java.util.stream.Collectors ;
//import java.util.stream.Stream ;
//
//import javax.net.ssl.SSLContext ;
//
//import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase ;
//import org.apache.hc.client5.http.config.RequestConfig ;
//import org.apache.hc.client5.http.impl.classic.CloseableHttpClient ;
//import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse ;
//import org.apache.hc.client5.http.impl.classic.HttpClients ;
//import org.apache.hc.client5.http.impl.io.ManagedHttpClientConnectionFactory ;
//import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager ;
//import org.apache.hc.client5.http.socket.ConnectionSocketFactory ;
//import org.apache.hc.client5.http.socket.PlainConnectionSocketFactory ;
//import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory ;
//import org.apache.hc.core5.http.ConnectionClosedException ;
//import org.apache.hc.core5.http.ContentLengthStrategy ;
//import org.apache.hc.core5.http.Header ;
//import org.apache.hc.core5.http.HttpHeaders ;
//import org.apache.hc.core5.http.HttpHost ;
//import org.apache.hc.core5.http.NameValuePair ;
//import org.apache.hc.core5.http.config.Registry ;
//import org.apache.hc.core5.http.config.RegistryBuilder ;
//import org.apache.hc.core5.http.impl.DefaultContentLengthStrategy ;
//import org.apache.hc.core5.http.impl.io.EmptyInputStream ;
//import org.apache.hc.core5.http.io.entity.ByteArrayEntity ;
//import org.apache.hc.core5.http.io.entity.InputStreamEntity ;
//import org.apache.hc.core5.http.protocol.BasicHttpContext ;
//import org.apache.hc.core5.http.protocol.HttpContext ;
//import org.apache.hc.core5.net.URIAuthority ;
//import org.apache.hc.core5.util.Timeout ;
//import org.slf4j.Logger ;
//import org.slf4j.LoggerFactory ;
//
////package com.github.dockerjava.httpclient5;
//
//import com.github.dockerjava.transport.DockerHttpClient ;
//import com.github.dockerjava.transport.SSLConfig ;
//import com.sun.jna.LastErrorException ;
//import com.sun.jna.Native ;
//import com.sun.jna.Platform ;
//import com.sun.jna.Structure ;
//import com.sun.jna.win32.StdCallLibrary ;
//import com.sun.jna.win32.W32APIOptions ;
//
//public class WrapperApacheDockerHttpClientImpl implements DockerHttpClient {
//
//	private final CloseableHttpClient httpClient ;
//	private final HttpHost host ;
//
//	public WrapperApacheDockerHttpClientImpl (
//			URI dockerHost,
//			SSLConfig sslConfig,
//			int maxConnections,
//			Timeout connectionTimeout,
//			Timeout responseTimeout ) {
//
//		Registry<ConnectionSocketFactory> socketFactoryRegistry = createConnectionSocketFactoryRegistry( sslConfig,
//				dockerHost ) ;
//
//		switch ( dockerHost.getScheme( ) ) {
//
//		case "unix":
//		case "npipe":
//			host = new HttpHost( dockerHost.getScheme( ), "localhost", 2375 ) ;
//			break ;
//		case "tcp":
//			host = new HttpHost(
//					socketFactoryRegistry.lookup( "https" ) != null ? "https" : "http",
//					dockerHost.getHost( ),
//					dockerHost.getPort( ) ) ;
//			break ;
//		default:
//			host = HttpHost.create( dockerHost ) ;
//
//		}
//
//		PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager(
//				socketFactoryRegistry,
//				new ManagedHttpClientConnectionFactory(
//						null,
//						null,
//						null,
//						null,
//						message -> {
//
//							Header transferEncodingHeader = message.getFirstHeader( HttpHeaders.TRANSFER_ENCODING ) ;
//
//							if ( transferEncodingHeader != null ) {
//
//								if ( "identity".equalsIgnoreCase( transferEncodingHeader.getValue( ) ) ) {
//
//									return ContentLengthStrategy.UNDEFINED ;
//
//								}
//
//							}
//
//							return DefaultContentLengthStrategy.INSTANCE.determineLength( message ) ;
//
//						},
//						null ) ) ;
//		connectionManager.setMaxTotal( maxConnections ) ;
//		connectionManager.setDefaultMaxPerRoute( maxConnections ) ;
//
//		// var connectTimeout = Timeout.ofMilliseconds( 5 );
//		// connectTimeout = Timeout.ofSeconds( 5 );
//		var connectionSettings = RequestConfig.custom( )
//				.setConnectTimeout( connectionTimeout )
//				.setConnectionRequestTimeout( connectionTimeout )
//				.setResponseTimeout( responseTimeout )
//				.build( ) ;
//
//		httpClient = HttpClients.custom( )
//				.setRequestExecutor( new WrapperHijackingHttpRequestExecutor( null ) )
//				.setConnectionManager( connectionManager )
//				.setDefaultRequestConfig( connectionSettings )
//				.build( ) ;
//
//	}
//
//	private Registry<ConnectionSocketFactory> createConnectionSocketFactoryRegistry (
//																						SSLConfig sslConfig ,
//																						URI dockerHost ) {
//
//		RegistryBuilder<ConnectionSocketFactory> socketFactoryRegistryBuilder = RegistryBuilder.create( ) ;
//
//		if ( sslConfig != null ) {
//
//			try {
//
//				SSLContext sslContext = sslConfig.getSSLContext( ) ;
//
//				if ( sslContext != null ) {
//
//					socketFactoryRegistryBuilder.register( "https", new SSLConnectionSocketFactory( sslContext ) ) ;
//
//				}
//
//			} catch ( Exception e ) {
//
//				throw new RuntimeException( e ) ;
//
//			}
//
//		}
//
//		return socketFactoryRegistryBuilder
//				.register( "tcp", PlainConnectionSocketFactory.INSTANCE )
//				.register( "http", PlainConnectionSocketFactory.INSTANCE )
//				.register( "unix", new PlainConnectionSocketFactory( ) {
//					@Override
//					public Socket createSocket ( HttpContext context ) throws IOException {
//
//						return new UnixDomainSocket( dockerHost.getPath( ) ) ;
//
//					}
//				} )
//				.register( "npipe", new PlainConnectionSocketFactory( ) {
//					@Override
//					public Socket createSocket ( HttpContext context ) {
//
//						return new NamedPipeSocket( dockerHost.getPath( ) ) ;
//
//					}
//				} )
//				.build( ) ;
//
//	}
//
//	@Override
//	public Response execute ( Request request ) {
//
//		HttpContext context = new BasicHttpContext( ) ;
//		HttpUriRequestBase httpUriRequest = new HttpUriRequestBase( request.method( ), URI.create( request.path( ) ) ) ;
//		httpUriRequest.setScheme( host.getSchemeName( ) ) ;
//		httpUriRequest.setAuthority( new URIAuthority( host.getHostName( ), host.getPort( ) ) ) ;
//
//		request.headers( ).forEach( httpUriRequest::addHeader ) ;
//
//		byte[] bodyBytes = request.bodyBytes( ) ;
//
//		if ( bodyBytes != null ) {
//
//			httpUriRequest.setEntity( new ByteArrayEntity( bodyBytes, null ) ) ;
//
//		} else {
//
//			InputStream body = request.body( ) ;
//
//			if ( body != null ) {
//
//				httpUriRequest.setEntity( new InputStreamEntity( body, null ) ) ;
//
//			}
//
//		}
//
//		if ( request.hijackedInput( ) != null ) {
//
//			context.setAttribute( WrapperHijackingHttpRequestExecutor.HIJACKED_INPUT_ATTRIBUTE, request
//					.hijackedInput( ) ) ;
//			httpUriRequest.setHeader( "Upgrade", "tcp" ) ;
//			httpUriRequest.setHeader( "Connection", "Upgrade" ) ;
//
//		}
//
//		try {
//
//			CloseableHttpResponse response = httpClient.execute( host, httpUriRequest, context ) ;
//
//			return new ApacheResponse( httpUriRequest, response ) ;
//
//		} catch ( IOException e ) {
//
//			throw new RuntimeException( e ) ;
//
//		}
//
//	}
//
//	@Override
//	public void close ( ) throws IOException {
//
//		httpClient.close( ) ;
//
//	}
//
//	static class ApacheResponse implements Response {
//
//		private static final Logger LOGGER = LoggerFactory.getLogger( ApacheResponse.class ) ;
//
//		private final HttpUriRequestBase request ;
//
//		private final CloseableHttpResponse response ;
//
//		ApacheResponse ( HttpUriRequestBase httpUriRequest, CloseableHttpResponse response ) {
//
//			this.request = httpUriRequest ;
//			this.response = response ;
//
//		}
//
//		@Override
//		public int getStatusCode ( ) {
//
//			return response.getCode( ) ;
//
//		}
//
//		@Override
//		public Map<String, List<String>> getHeaders ( ) {
//
//			return Stream.of( response.getHeaders( ) ).collect( Collectors.groupingBy(
//					NameValuePair::getName,
//					Collectors.mapping( NameValuePair::getValue, Collectors.toList( ) ) ) ) ;
//
//		}
//
//		@Override
//		public String getHeader ( String name ) {
//
//			Header firstHeader = response.getFirstHeader( name ) ;
//			return firstHeader != null ? firstHeader.getValue( ) : null ;
//
//		}
//
//		@Override
//		public InputStream getBody ( ) {
//
//			try {
//
//				return response.getEntity( ) != null
//						? response.getEntity( ).getContent( )
//						: EmptyInputStream.INSTANCE ;
//
//			} catch ( IOException e ) {
//
//				throw new RuntimeException( e ) ;
//
//			}
//
//		}
//
//		@Override
//		public void close ( ) {
//
//			try {
//
//				request.abort( ) ;
//
//			} catch ( Exception e ) {
//
//				LOGGER.debug( "Failed to abort the request", e ) ;
//
//			}
//
//			try {
//
//				response.close( ) ;
//
//			} catch ( ConnectionClosedException e ) {
//
//				LOGGER.trace( "Failed to close the response", e ) ;
//
//			} catch ( Exception e ) {
//
//				LOGGER.debug( "Failed to close the response", e ) ;
//
//			}
//
//		}
//	}
//
//	static class UnixDomainSocket extends Socket {
//
//		private static final int AF_UNIX = 1 ;
//		private static final int SOCK_STREAM = Platform.isSolaris( ) ? 2 : 1 ;
//		private static final int PROTOCOL = 0 ;
//
//		static {
//
//			if ( Platform.isSolaris( ) ) {
//
//				System.loadLibrary( "nsl" ) ;
//				System.loadLibrary( "socket" ) ;
//
//			}
//
//			if ( ! Platform.isWindows( ) && ! Platform.isWindowsCE( ) ) {
//
//				Native.register( "c" ) ;
//
//			}
//
//		}
//
//		private final AtomicBoolean closeLock = new AtomicBoolean( ) ;
//		private final SockAddr sockaddr ;
//		private final int fd ;
//		private InputStream is ;
//		private OutputStream os ;
//		private boolean connected ;
//
//		UnixDomainSocket ( String path ) throws IOException {
//
//			if ( Platform.isWindows( ) || Platform.isWindowsCE( ) ) {
//
//				throw new IOException( "Unix domain sockets are not supported on Windows" ) ;
//
//			}
//
//			sockaddr = new SockAddr( path ) ;
//			closeLock.set( false ) ;
//
//			try {
//
//				fd = socket( AF_UNIX, SOCK_STREAM, PROTOCOL ) ;
//
//			} catch ( LastErrorException lee ) {
//
//				throw new IOException( "native socket() failed : " + formatError( lee ) ) ;
//
//			}
//
//		}
//
//		public static native int socket ( int domain , int type , int protocol ) throws LastErrorException ;
//
//		public static native int connect ( int sockfd , SockAddr sockaddr , int addrlen )
//			throws LastErrorException ;
//
//		public static native int read ( int fd , byte[] buffer , long size )
//			throws LastErrorException ;
//
//		public static native int send ( int fd , byte[] buffer , int count , int flags )
//			throws LastErrorException ;
//
//		public static native int close ( int fd ) throws LastErrorException ;
//
//		public static native String strerror ( int errno ) ;
//
//		private static String formatError ( LastErrorException lee ) {
//
//			try {
//
//				return strerror( lee.getErrorCode( ) ) ;
//
//			} catch ( Throwable t ) {
//
//				return lee.getMessage( ) ;
//
//			}
//
//		}
//
//		@Override
//		public boolean isConnected ( ) {
//
//			return connected ;
//
//		}
//
//		@Override
//		public void close ( ) throws IOException {
//
//			if ( ! closeLock.getAndSet( true ) ) {
//
//				try {
//
//					close( fd ) ;
//
//				} catch ( LastErrorException lee ) {
//
//					throw new IOException( "native close() failed : " + formatError( lee ) ) ;
//
//				}
//
//				connected = false ;
//
//			}
//
//		}
//
//		@Override
//		public void connect ( SocketAddress endpoint ) throws IOException {
//
//			connect( endpoint, 0 ) ;
//
//		}
//
//		public void connect ( SocketAddress endpoint , int timeout ) throws IOException {
//
//			try {
//
//				int ret = connect( fd, sockaddr, sockaddr.size( ) ) ;
//
//				if ( ret != 0 ) {
//
//					throw new IOException( strerror( Native.getLastError( ) ) ) ;
//
//				}
//
//				connected = true ;
//
//			} catch ( LastErrorException lee ) {
//
//				throw new IOException( "native connect() failed : " + formatError( lee ) ) ;
//
//			}
//
//			is = new UnixSocketInputStream( ) ;
//			os = new UnixSocketOutputStream( ) ;
//
//		}
//
//		public InputStream getInputStream ( ) {
//
//			return is ;
//
//		}
//
//		public OutputStream getOutputStream ( ) {
//
//			return os ;
//
//		}
//
//		public void setTcpNoDelay ( boolean b ) {
//
//			// do nothing
//		}
//
//		public void setKeepAlive ( boolean b ) {
//
//			// do nothing
//		}
//
//		public void setReceiveBufferSize ( int size ) {
//
//			// do nothing
//		}
//
//		public void setSendBufferSize ( int size ) {
//
//			// do nothing
//		}
//
//		public void setSoLinger ( boolean b , int i ) {
//
//			// do nothing
//		}
//
//		public void setSoTimeout ( int timeout ) {
//
//		}
//
//		public void shutdownInput ( ) {
//
//			// do nothing
//		}
//
//		public void shutdownOutput ( ) {
//
//			// do nothing
//		}
//
//		public static class SockAddr extends Structure {
//
//			@SuppressWarnings ( "checkstyle:membername" )
//			public short sun_family ;
//			@SuppressWarnings ( "checkstyle:membername" )
//			public byte[] sun_path ;
//
//			/**
//			 * Contructor.
//			 *
//			 * @param sunPath path
//			 */
//			SockAddr ( String sunPath ) {
//
//				sun_family = AF_UNIX ;
//				byte[] arr = sunPath.getBytes( ) ;
//				sun_path = new byte[arr.length + 1] ;
//				System.arraycopy( arr, 0, sun_path, 0, Math.min( sun_path.length - 1, arr.length ) ) ;
//				allocateMemory( ) ;
//
//			}
//
//			@Override
//			protected java.util.List<String> getFieldOrder ( ) {
//
//				return Arrays.asList( "sun_family", "sun_path" ) ;
//
//			}
//		}
//
//		class UnixSocketInputStream extends InputStream {
//
//			@Override
//			public int read ( byte[] bytesEntry , int off , int len ) throws IOException {
//
//				if ( ! isConnected( ) ) {
//
//					return -1 ;
//
//				}
//
//				try {
//
//					if ( off > 0 ) {
//
//						byte[] data = new byte[( len < 10240 ) ? len : 10240] ;
//						int size = UnixDomainSocket.read( fd, data, data.length ) ;
//
//						if ( size <= 0 ) {
//
//							return -1 ;
//
//						}
//
//						System.arraycopy( data, 0, bytesEntry, off, size ) ;
//						return size ;
//
//					} else {
//
//						int size = UnixDomainSocket.read( fd, bytesEntry, len ) ;
//
//						if ( size <= 0 ) {
//
//							return -1 ;
//
//						}
//
//						return size ;
//
//					}
//
//				} catch ( LastErrorException lee ) {
//
//					throw new IOException( "native read() failed : " + formatError( lee ) ) ;
//
//				}
//
//			}
//
//			@Override
//			public int read ( ) throws IOException {
//
//				byte[] bytes = new byte[1] ;
//				int bytesRead = read( bytes ) ;
//
//				if ( bytesRead <= 0 ) {
//
//					return -1 ;
//
//				}
//
//				return bytes[ 0 ] & 0xff ;
//
//			}
//
//			@Override
//			public int read ( byte[] bytes ) throws IOException {
//
//				if ( ! isConnected( ) ) {
//
//					return -1 ;
//
//				}
//
//				return read( bytes, 0, bytes.length ) ;
//
//			}
//		}
//
//		class UnixSocketOutputStream extends OutputStream {
//
//			@Override
//			public void write ( byte[] bytesEntry , int off , int len ) throws IOException {
//
//				int bytes ;
//
//				try {
//
//					if ( off > 0 ) {
//
//						int size ;
//						int remainingLength = len ;
//						byte[] data = new byte[( len < 10240 ) ? len : 10240] ;
//
//						do {
//
//							size = ( remainingLength < 10240 ) ? remainingLength : 10240 ;
//							System.arraycopy( bytesEntry, off, data, 0, size ) ;
//
//							if ( ! isConnected( ) ) {
//
//								return ;
//
//							}
//
//							bytes = UnixDomainSocket.send( fd, data, size, 0 ) ;
//
//							if ( bytes > 0 ) {
//
//								off += bytes ;
//								remainingLength -= bytes ;
//
//							}
//
//						} while ( ( remainingLength > 0 ) && ( bytes > 0 ) ) ;
//
//					} else {
//
//						if ( ! isConnected( ) ) {
//
//							return ;
//
//						}
//
//						bytes = UnixDomainSocket.send( fd, bytesEntry, len, 0 ) ;
//
//					}
//
//					if ( bytes != len ) {
//
//						throw new IOException( "can't write " + len + "bytes" ) ;
//
//					}
//
//				} catch ( LastErrorException lee ) {
//
//					throw new IOException( "native write() failed : " + formatError( lee ) ) ;
//
//				}
//
//			}
//
//			@Override
//			public void write ( int value ) throws IOException {
//
//				write( new byte[] {
//						(byte) value
//				} ) ;
//
//			}
//
//			@Override
//			public void write ( byte[] bytes ) throws IOException {
//
//				write( bytes, 0, bytes.length ) ;
//
//			}
//		}
//	}
//
//	static class NamedPipeSocket extends Socket {
//
//		private final String socketFileName ;
//
//		private AsynchronousFileByteChannel channel ;
//
//		NamedPipeSocket ( String socketFileName ) {
//
//			this.socketFileName = socketFileName ;
//
//		}
//
//		@Override
//		public void close ( ) throws IOException {
//
//			if ( channel != null ) {
//
//				channel.close( ) ;
//
//			}
//
//		}
//
//		@Override
//		public void connect ( SocketAddress endpoint ) throws IOException {
//
//			connect( endpoint, 0 ) ;
//
//		}
//
//		@Override
//		public void connect ( SocketAddress endpoint , int timeout ) throws IOException {
//
//			long startedAt = System.currentTimeMillis( ) ;
//			timeout = Math.max( timeout, 10_000 ) ;
//
//			while ( true ) {
//
//				try {
//
//					channel = new AsynchronousFileByteChannel(
//							AsynchronousFileChannel.open(
//									Paths.get( socketFileName ),
//									StandardOpenOption.READ,
//									StandardOpenOption.WRITE ) ) ;
//					break ;
//
//				} catch ( FileSystemException e ) {
//
//					if ( System.currentTimeMillis( ) - startedAt >= timeout ) {
//
//						throw new RuntimeException( e ) ;
//
//					} else {
//
//						Kernel32.INSTANCE.WaitNamedPipe( socketFileName, 100 ) ;
//
//					}
//
//				}
//
//			}
//
//		}
//
//		@Override
//		public InputStream getInputStream ( ) {
//
//			return Channels.newInputStream( channel ) ;
//
//		}
//
//		@Override
//		public OutputStream getOutputStream ( ) {
//
//			return Channels.newOutputStream( channel ) ;
//
//		}
//
//		interface Kernel32 extends StdCallLibrary {
//
//			Kernel32 INSTANCE = Native.load( "kernel32", Kernel32.class, W32APIOptions.DEFAULT_OPTIONS ) ;
//
//			@SuppressWarnings ( "checkstyle:methodname" )
//			boolean WaitNamedPipe ( String lpNamedPipeName , int nTimeOut ) ;
//		}
//
//		private static class AsynchronousFileByteChannel implements AsynchronousByteChannel {
//			private final AsynchronousFileChannel fileChannel ;
//
//			AsynchronousFileByteChannel ( AsynchronousFileChannel fileChannel ) {
//
//				this.fileChannel = fileChannel ;
//
//			}
//
//			@Override
//			public <A> void read ( ByteBuffer dst , A attachment , CompletionHandler<Integer, ? super A> handler ) {
//
//				fileChannel.read( dst, 0, attachment, new CompletionHandler<Integer, A>( ) {
//					@Override
//					public void completed ( Integer read , A attachment ) {
//
//						handler.completed( read > 0 ? read : -1, attachment ) ;
//
//					}
//
//					@Override
//					public void failed ( Throwable exc , A attachment ) {
//
//						if ( exc instanceof AsynchronousCloseException ) {
//
//							handler.completed( -1, attachment ) ;
//							return ;
//
//						}
//
//						handler.failed( exc, attachment ) ;
//
//					}
//				} ) ;
//
//			}
//
//			@Override
//			public Future<Integer> read ( ByteBuffer dst ) {
//
//				CompletableFutureHandler future = new CompletableFutureHandler( ) ;
//				fileChannel.read( dst, 0, null, future ) ;
//				return future ;
//
//			}
//
//			@Override
//			public <A> void write ( ByteBuffer src , A attachment , CompletionHandler<Integer, ? super A> handler ) {
//
//				fileChannel.write( src, 0, attachment, handler ) ;
//
//			}
//
//			@Override
//			public Future<Integer> write ( ByteBuffer src ) {
//
//				return fileChannel.write( src, 0 ) ;
//
//			}
//
//			@Override
//			public void close ( ) throws IOException {
//
//				fileChannel.close( ) ;
//
//			}
//
//			@Override
//			public boolean isOpen ( ) {
//
//				return fileChannel.isOpen( ) ;
//
//			}
//
//			private static class CompletableFutureHandler extends CompletableFuture<Integer> implements
//					CompletionHandler<Integer, Object> {
//
//				@Override
//				public void completed ( Integer read , Object attachment ) {
//
//					complete( read > 0 ? read : -1 ) ;
//
//				}
//
//				@Override
//				public void failed ( Throwable exc , Object attachment ) {
//
//					if ( exc instanceof AsynchronousCloseException ) {
//
//						complete( -1 ) ;
//						return ;
//
//					}
//
//					completeExceptionally( exc ) ;
//
//				}
//			}
//		}
//	}
//
//}
