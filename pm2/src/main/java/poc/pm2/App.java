package poc.pm2;

/**
 * Hello world!
 *
 */
public class App
{
    public static void main( String[] args )
    {
        System.out.println( "Hello World!" );
    }

    public String hoge() {
    	var func = new FunctionHoge();
    	return func.hoge();
	}
}
