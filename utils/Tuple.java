package florifulgurator.logsocket.utils;
// Instead of florifulgurator.logsocket.yseq.FnctlTuple
// TODO test which is better qwertz

public class Tuple<S, T> {
	public S t1;
	public T t2;
	public Tuple(S s, T t) {this.t1=s; this.t2=t;}
	public String toString() {return "("+this.t1.toString()+", "+this.t2.toString()+")";}



// >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
	public static class TEST {

		// 2D linear algebra: Rotate 2-vector 30° counterclockwise
		public static Tuple<Double,Double> rot30(Tuple<Double,Double> v) {
			double rd = Math.toRadians(30);
			return new Tuple<Double,Double>(Math.cos(rd)*v.t1-Math.sin(rd)*v.t2, Math.sin(rd)*v.t1+Math.cos(rd)*v.t2  );
			//return v.map( (x,y)-> Math.cos(rd)*x-Math.sin(rd)*y, (x,y)->  Math.sin(rd)*x+Math.cos(rd)*y );
		} 


		public static void main(String[] args) {

			System.out.println(">>>>>>>>>> Testing Tuple >>>>>>>>>>\n");
		
			Tuple<Double,Double> xy = new Tuple<Double,Double>(1.0, 0.0);
			//FnctlTuple<Double,Double> rot30xy = rot30(xy);
			//TupleObject<Double,Double>  tpo = rot30xy.toObject();
			
			System.out.println("Rotating vector ("+xy.t1+","+xy.t2+")");
			System.out.println("90° counterclockwise:  "+ rot30(rot30(rot30(xy))).toString());
			System.out.println("180° counterclockwise: "+ rot30(rot30(rot30(rot30(rot30(rot30(xy)))))).toString());
//90° counterclockwise:  (2.7755575615628914E-16, 1.0)
//180° counterclockwise: (-1.0, 5.551115123125783E-16)
//Same error as Seq FnctlTuple
			System.out.println("xy: ("+xy.t1+","+xy.t2+")");
			Tuple<Double,Double> xy2 = new Tuple<Double,Double>(1.0, 0.0);
			System.out.println("xy.equals(xy2) "+xy.equals(xy2));
			Tuple<String,String> lala = new Tuple<String,String>("la","lo");
			Tuple<String,String> lala2 = new Tuple<String,String>("la","lo");
			System.out.println("lala.equals(lala2) "+lala.equals(lala2));


			
		}
	}
}


