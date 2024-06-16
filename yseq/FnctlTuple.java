package florifulgurator.logsocket.yseq;

// Inspired by https://www.alibabacloud.com/blog/a-new-stream-adding-the-generator-feature-to-java_600593
// (The Canonical Short Intro to functional programming in Java...)
// which is also about "adding the tuple feature to Java",
// i.e. tuples of objects without requiring to explicitly specify/ construct a data transfer object in the beginning...
// i.e. functional interface-based (vs. object class-based) tuples.
// Only when reading the tuple members, a DTO needs might be created (and a convention on object member names introduced for the tuple components).

// // E.g. define
// ArrayList< FnctlTuple<Integer, WeakReference<Lggr>> > lggrList
// // without any extra boilerplate
// // Then do
// lggrList.add (FnctlTuple.of(42, new WeakReference<Lggr>(newLggr)) )
// // Only later, when reading we might need a TupleObject class;
// TupleObject<Integer,WeakReference<Lggr>> tpo = tuple.toObject()

// A Consumer of Biconsumers might look weird, but there is a fundamental abstract principle behind it:
// The mathematician might be reminded of the canonical map from a space to its double dual,
// and the physicist of the triad of states, observables, measurements.

import java.util.function.BiConsumer;
import java.util.function.BiFunction;

@FunctionalInterface
public interface FnctlTuple<S, T> { //!!!!!!!!...

	void eval(BiConsumer<S, T> xyz); // eval to void may be extended to eval to <E>
 
	static <S, T> FnctlTuple<S, T> of(S s, T t) { return bc -> bc.accept(s, t);	}


	default boolean equals(FnctlTuple<S,T> tpl) {
		boolean ret[] = {false}; // effectively final
		eval( (s,t) -> tpl.eval( (t1,t2) -> {ret[0] = (s.equals(t1) && t.equals(t2));} ));
		return ret[0];
	}

	default String bakeToString() { // toString() not possible in interface
		String[] str = {""}; // effectively final
		eval( (s,t) -> {str[0] = "("+s+", "+t+")";} );
		return str[0];
	}

	// To make a tuple of Bifunctions into a function of tuples: FnctlTuple<S,T> --> FnctlTuple<U V>
	// See TEST below for example.
	default <U, V> FnctlTuple<U,V> map(BiFunction<S,T, U> f1, BiFunction<S,T, V> f2) {
		return bc -> eval( (s,t) -> bc.accept(f1.apply(s,t), f2.apply(s,t)) );
	}


	public static class TupleObject<S, T> {  //!!!!!!!!...
		public S t1;
		public T t2;
	}
	// ---
	default TupleObject<S, T> toObject() {
		TupleObject<S, T> dto = new TupleObject<>();
		eval( (s,t) -> {dto.t1=s; dto.t2=t;} );
		return dto;
	}
	


// >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
	public static class TEST {

		// 2D linear algebra: Rotate 2-vector 30° counterclockwise
		public static FnctlTuple<Double,Double> rot30(FnctlTuple<Double,Double> v) {
			double rd = Math.toRadians(30);
			return v.map( (x,y) -> Math.cos(rd)*x-Math.sin(rd)*y, (x,y) -> Math.sin(rd)*x+Math.cos(rd)*y );
		} 


		public static void main(String[] args) {

			System.out.println(">>>>>>>>>> Testing FnctlTuple >>>>>>>>>>\n");
		
			Double ddd = 0.0;
			FnctlTuple<Double,Double> xy = FnctlTuple.of(1.0, ddd);
			System.out.println( "xy ==  "+ xy.bakeToString());
			ddd = 7.7;
			System.out.println( "xy ==  "+ xy.bakeToString());
					
			
			FnctlTuple<Double,Double> rot30xy = rot30(xy);
			TupleObject<Double,Double>  tpo = rot30xy.toObject();
			
			System.out.println( "Rotating vector ("+xy.toObject().t1+","+xy.toObject().t2+")");
			System.out.println( "30° counterclockwise: ("+tpo.t1+", "+tpo.t2+")");
			System.out.println( "90° counterclockwise:  "+ rot30(rot30(rot30(xy))).bakeToString());
			System.out.println( "180° counterclockwise: "+ rot30(rot30(rot30(rot30(rot30(rot30(xy)))))).bakeToString());

			FnctlTuple<Double,Double> xy2 = FnctlTuple.of(1.0, 0.0);
			System.out.println(" xy.equals(xy2) " +  xy.equals(xy2) ); // "xy.equals(xy2) true"
			System.out.println(" xy==xy2 "        + (xy==xy2) );       // "xy==xy2        false"
			
			FnctlTuple<String,String> lala = FnctlTuple.of("la", new String("lo"));
			FnctlTuple<String,String> lala2 = FnctlTuple.of(new String("la"), "lo");
			System.out.println(" lala.equals(lala2) " +  lala.equals(lala2) ); // "lala.equals(lala2) true"
			System.out.println(" lala==lala2 "        + (lala==lala2) );       // "lala==lala2        false"
		}
	}
}
