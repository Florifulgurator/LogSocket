package florifulgurator.logsocket.YSeq;

// Inspired by https://www.alibabacloud.com/blog/a-new-stream-adding-the-generator-feature-to-java_600593
// which actually is also about "adding the tuple feature to Java",
// i.e. tuples of objects without requiring to explicitly specify/ construct a data transfer object in the beginning.
// Only when reading the tuple members, a DTO needs to be created.

// E.g. define ArrayList<Tuple<Integer,WeakReference<Lggr>>> lggrList without any extra boilerplate
// then do lggrList.add(Tuple.of(n2, new WeakReference<Lggr>(newLggr)))
// Only later, when reading we need TupleObject<Integer,WeakReference<Lggr>> tpo = tuple.toObject()


import java.util.function.BiConsumer;
import java.util.function.BiFunction;


public interface Tuple<S, T> {
    void consume(BiConsumer<S, T> consumer);
        
 
    static <S, T> Tuple<S, T> of(S s, T t) {
    	return bc -> bc.accept(s, t) ;
	}

	// To make a tuple of Bifunctions into a function of tuples: Tuple<S,T> --> Tuple<U V>
	// See TEST below for example.
	default <U, V> Tuple<U,V> map(BiFunction<S,T, U> f1, BiFunction<S,T, V> f2) {
		return bc -> consume( (s,t) -> bc.accept(f1.apply(s,t), f2.apply(s,t)) );
	}

	
	public static class TupleObject<S, T> {
		public S t1;
		public T t2;
	}
	// ---
	default TupleObject<S, T> toObject() {
		TupleObject<S, T> dto = new TupleObject<>();
		consume( (s,t)->{dto.t1=s; dto.t2=t;} );
		return dto;
	}


	default String bakeToString() { // toString() not possible in interface
		String[] Str = {""};
		consume( (s,t)->{ Str[0] = "("+s+", "+t+")"; } );
		return Str[0];
	}



// >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
	public static class TEST {

		// 2D linear algebra: Rotate 2-vector 30° counterclockwise
		public static Tuple<Double,Double> rot30(Tuple<Double,Double> v) {
			double rd = Math.toRadians(30);
			return v.map( (x,y)-> Math.cos(rd)*x-Math.sin(rd)*y, (x,y)->  Math.sin(rd)*x+Math.cos(rd)*y );
		} 


		public static void main(String[] args) {

			System.out.println(">>>>>>>>>> Testing Tuple >>>>>>>>>>\n");
		
			Tuple<Double,Double> xy = Tuple.of(1.0, 0.0);
			Tuple<Double,Double> rot30xy = rot30(xy);
			TupleObject<Double,Double>  tpo = rot30xy.toObject();
			
			System.out.println("Rotating vector ("+xy.toObject().t1+","+xy.toObject().t2+")");
			System.out.println("30° counterclockwise: ("+tpo.t1+", "+tpo.t2+")");
			System.out.println("90° counterclockwise:  "+ rot30(rot30(rot30(xy))).bakeToString());
			System.out.println("180° counterclockwise: "+ rot30(rot30(rot30(rot30(rot30(rot30(xy)))))).bakeToString());
		
	}

}

}
