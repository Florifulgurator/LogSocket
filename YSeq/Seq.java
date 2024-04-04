package florifulgurator.logsocket.YSeq;
// Since a "Seq" aleady exists in Scala and jOOQ I name the package "YSeq" after the @author Lei Wen (Yilai).

// TEST/Example with Fibonacci series below.

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Ingeniously simple and efficient idea,
// ideal point of entry to get started on serious lazy-evaluation functional programming in Java:
// https://www.alibabacloud.com/blog/a-new-stream-adding-the-generator-feature-to-java_600593
// 
// QUOTE:
// (...)
// What I want to emphasize again is that all of the above content, code, features, and cases, and the CsvReader series I have implemented,
// derive from this simple interface, which is the source of everything, and it is completely worth writing again at the end of this article.
//
//     public interface Seq<T> {
//         void consume(Consumer<T> consumer);
//     }
//
// (...)
//////////////////////////////////////////////////////////////////////////////////////////////////////////////////

// Few typos and naming corrected without comment.
// All comments mine #MG
// My additions/experiments/bug marked by #MG


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.StringJoiner;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;




public interface Seq<T> {
	void consume(Consumer<T> consumer);


	@SafeVarargs
	static <T> Seq<T> of(T... ts) {
		return Arrays.asList(ts)::forEach; // == c -> Arrays.asList(ts).forEach(c) ==  c -> Arrays.asList(ts).forEach( t -> c.accept(t) )
	}

	@SuppressWarnings("serial")
	public final static class SeqStopException extends RuntimeException {
		public static final SeqStopException INSTANCE = new SeqStopException();
	
	    @Override
	    public synchronized Throwable fillInStackTrace() {
	        return this;
	    }
	}

	static <T> T stop() {
		throw SeqStopException.INSTANCE;
	}

	default <C> void consumeTillStop(Consumer<T> consumer) {
		try {
			consume(consumer);
		} catch (SeqStopException ignore) {}
	}

	default Seq<T> take(int n) {
		return c -> {
			int[] i = {n};
			consumeTillStop(t -> {
				if (i[0]-- > 0) {
					c.accept(t); // equivalent to the usual yield
				} else {
					stop();
				}
			});
		};
	}
	
	default Seq<T> filter(Predicate<T> predicate) {
		return c -> consume(t -> {
			if (predicate.test(t)) {
				c.accept(t);
			}
		});
	}

	// Seq<T> ==> Seq<E>
	default <E> Seq<E> map(Function<T, E> function) {
		return c -> consume(t -> c.accept(function.apply(t)));
	}


	default <E, R> Seq<R> zip(Iterable<E> iterable, BiFunction<T, E, R> function) {
		return c -> {
			Iterator<E> iterator = iterable.iterator();
			consumeTillStop(t -> {
				if (iterator.hasNext()) {
					c.accept(function.apply(t, iterator.next()));
				} else {
					stop();
				}
			});
		};
	}


	default ArrayList<T> toArrayList() { 	// Clumsy Java stream would need [...].collect(Collectors.toCollection(ArrayList::new))
		ArrayList<T> list = new ArrayList<>();
		consume(list::add);
		return list;
	}
		
		
	default String join(String sep) {
		StringJoiner joiner = new StringJoiner(sep) ;
		consume(t -> joiner.add(t.toString()));
		return joiner.toString();
	}

		
	@SuppressWarnings("serial")
	public static class ArraySeq<T> extends ArrayList<T> implements Seq<T> {
		@Override
		public void consume(Consumer<T> consumer) {
			forEach(consumer);
		}
	}
	
	default ArraySeq<T> cache() {
		ArraySeq<T> arraySeq = new ArraySeq<>();
		consume(t -> arraySeq.add(t));
		return arraySeq;
	}
	
	default Seq<T> parallel() {
		ForkJoinPool pool = ForkJoinPool.commonPool();
		return c -> map(t -> pool.submit(() -> c.accept(t))).cache().consume(ForkJoinTask::join);
	}
	
	
	// #MG 
	default <E, R> Seq<R> zipSupp(Supplier<E> supplier, BiFunction<T, E, R> function) { // has bug!
		return c -> consume( t -> c.accept(function.apply(t, supplier.get())));
	}
	default <E> void zipSuppCons(Supplier<E> supplier, BiConsumer<T, E> bcons) { // #MG
		consume( t -> bcons.accept(t, supplier.get()));
	}

	default int length() { // #MG Nonsense!
		int[] i = {0};
		consume(t -> {++i[0];} );
		return i[0];
	}

	


	
// >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
	public static class TEST {

		public static String shortClObjID(Object o) { return (o==null)?"null":Stream.of(o.toString().split("[.]")).reduce((first,last)->last).get(); }

		public static Seq<Integer> fibonacci(boolean[] stopped) {
			return c -> {
				int i = 0, j = 1; // #MG
				c.accept(i);
				c.accept(j);
				while (true) {
					System.out.print("while (true) {...");
					try {
						c.accept( j = Math.addExact(i, (i = j)) ); // else overflow (sign switch) without exception #MG
					} catch (Exception e) {
						System.err.println("fibonacci() exception "+e.getClass().getName()+" message: "+ e.getMessage());
						stopped[0]=true;
						stop();
					}
					System.out.println("...}");
				}
			};
		}


		static Supplier<Double> rando = Math::random;
		static Consumer<Double> println = dbl -> System.out.print("consuming "+dbl);
		static Seq<Consumer<Double>> printer = c -> { while(true) {System.out.print("c.accept(println) c="+shortClObjID(c)+" "); c.accept(println); System.out.println("Hurz!");} };

		static Seq<Consumer<Double>> printRando = printer.zipSupp(rando, (c,d) -> {System.out.print("c.accept(d) c="+shortClObjID(c)+" "); c.accept(d); return c;}); 
		
	
	
		
		public static void main(String[] args) {

			System.out.println(">>>>>>>>>> Testing Seq >>>>>>>>>>\n");

			System.out.println("printer.take(5).length()=="+printer.take(5).length());
			System.out.println("---------------------\n");

			System.out.println("printer.zipSupp(rando, (c,d) -> {c.accept(d); return c;}).take(5)");
			System.out.println("---------------------"+printRando.take(5).length()  //OK
			); // !!!!!! BUT prints 6x !!!!!! FIXME
			
			System.out.println("printer.take(5).zipSuppCons(rando, (c,d) -> c.accept(d))");
			printer.take(5).zipSuppCons(rando, (c,d) -> {System.out.print("c.accept(d) c="+shortClObjID(c)+" "); c.accept(d);}); //OK
			System.out.println("---------------------\n\n");

			System.out.println("fibonacci() test:");

			boolean[] stopped = {false}; // effectively final :-)
			Seq<Integer> f = fibonacci(stopped);
			System.out.println("stopped="+stopped[0]+ "  "+ f.take(10).length() );
			System.out.println("stopped="+stopped[0]+ "  "+ f.take(1000).join(", ")); // long overflow at F(93)=7540113804746346429

		}
	}

}

