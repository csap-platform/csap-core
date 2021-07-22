package org.csap.agent.services ;

import java.util.function.BiFunction ;
import java.util.function.Consumer ;

public class ClassMatcher {

	private final BiFunction<Object, Consumer<Object>, Boolean> binder ;

	private ClassMatcher ( BiFunction<Object, Consumer<Object>, Boolean> next ) {

		this.binder = next ;

	}

	public void exec ( Object o ) {

		binder.apply( o, null ) ;

	}

	public <Y> ClassMatcher with ( final Class<Y> targetClass , final Consumer<Y> consumer ) {

		return new ClassMatcher( ( obj , next ) -> {

			if ( binder.apply( obj, next ) ) {

				return true ;

			}

			if ( targetClass.isAssignableFrom( obj.getClass( ) ) ) {

				final Y as = (Y) obj ;

				consumer.accept( as ) ;

				return true ;

			}

			return false ;

		} ) ;

	}

	public ClassMatcher fallthrough ( final Consumer<Object> consumer ) {

		return new ClassMatcher( ( obj , next ) -> {

			if ( binder.apply( obj, next ) ) {

				return true ;

			}

			consumer.accept( obj ) ;

			return true ;

		} ) ;

	}

	public static ClassMatcher match ( ) {

		return new ClassMatcher( ( a , b ) -> false ) ;

	}
}
