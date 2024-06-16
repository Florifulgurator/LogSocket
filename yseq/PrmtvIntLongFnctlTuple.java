package florifulgurator.logsocket.yseq;

@FunctionalInterface
public interface PrmtvIntLongFnctlTuple {

	void eval(PrmtvIntLongBiConsumer xyz); // eval to void may be extended to eval to <E>
 
	static PrmtvIntLongFnctlTuple of(int s, long t) { return bc -> bc.accept(s, t);	}

	default String bakeToString() { // toString() not possible in interface
		String[] str = {""}; // effectively final
		eval( (s,t) -> {str[0] = "("+s+", "+t+")";} );
		return str[0];
	}


// >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
	public static class TEST {
	
		public static void main(String[] args) {
			System.out.println(">>>>>>>>>> Testing PrmtvIntLongFnctlTuple >>>>>>>>>>\n");

			PrmtvIntLongFnctlTuple x = PrmtvIntLongFnctlTuple.of(1, 2L);
			Long l = 3L;
			PrmtvIntLongFnctlTuple y = PrmtvIntLongFnctlTuple.of(1, l);
			l = 0L;
			System.out.println( "x:  "+ x.bakeToString());
			System.out.println( "y:  "+ y.bakeToString());
			
			
			
		}
		

	}
}