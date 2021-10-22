package org.csap.agent.linux ;

import java.io.BufferedOutputStream ;
import java.io.File ;
import java.io.FileInputStream ;
import java.io.FileOutputStream ;
import java.io.IOException ;
import java.io.InputStream ;
import java.util.Enumeration ;
import java.util.zip.ZipEntry ;
import java.util.zip.ZipFile ;
import java.util.zip.ZipOutputStream ;

public class ZipUtility {

	public static final void zipDirectory ( File directory , File zip )
		throws IOException {

		ZipOutputStream zos = new ZipOutputStream( new FileOutputStream( zip ) ) ;
		zip( directory, directory, zos ) ;
		zos.close( ) ;

	}

	public static final void zipFile ( File file , File zip )
		throws IOException {

		ZipOutputStream zos = new ZipOutputStream( new FileOutputStream( zip ) ) ;
		zip( file, file, zos ) ;
		zos.close( ) ;

	}

	private static final void zip ( File target , File base , ZipOutputStream zos )
		throws IOException {

		File[] files ;

		File targetInZip = base ;

		if ( target.isDirectory( ) ) {

			files = target.listFiles( ) ;

		} else {

			files = new File[1] ;
			files[ 0 ] = target ;
			targetInZip = target.getParentFile( ) ;

		}

		byte[] buffer = new byte[8192] ;
		int read = 0 ;

		for ( int i = 0, n = files.length; i < n; i++ ) {

			if ( files[ i ].isDirectory( ) ) {

				zip( files[ i ], targetInZip, zos ) ;

			} else {

				FileInputStream in = new FileInputStream( files[ i ] ) ;
				ZipEntry entry = new ZipEntry( files[ i ].getPath( ).substring(
						targetInZip.getPath( ).length( ) + 1 ) ) ;
				zos.putNextEntry( entry ) ;

				while ( -1 != ( read = in.read( buffer ) ) ) {

					zos.write( buffer, 0, read ) ;

				}

				in.close( ) ;

			}

		}

	}

	public static final void unzip ( File zip , File extractTo )
		throws IOException {

		try ( ZipFile archive = new ZipFile( zip ) ) {

			Enumeration<?> e = archive.entries( ) ;

			while ( e.hasMoreElements( ) ) {

				ZipEntry entry = (ZipEntry) e.nextElement( ) ;
				File file = new File( extractTo, entry.getName( ) ) ;

				if ( entry.isDirectory( ) && ! file.exists( ) ) {

					file.mkdirs( ) ;

				} else {

					if ( ! file.getParentFile( ).exists( ) ) {

						file.getParentFile( ).mkdirs( ) ;

					}

					InputStream in = archive.getInputStream( entry ) ;

					try ( BufferedOutputStream out = new BufferedOutputStream(
							new FileOutputStream( file ) ) ) {

						byte[] buffer = new byte[8192] ;
						int read ;

						while ( -1 != ( read = in.read( buffer ) ) ) {

							out.write( buffer, 0, read ) ;

						}

					}

				}

			}

		}

	}
}
