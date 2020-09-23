package poc.pm2;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

/**
 * Unit test for simple App.
 */
@RunWith(PowerMockRunner.class)
//@RunWith(MockitoJUnitRunner.class)
@PrepareForTest({App.class})
public class AppTest
{
//	@Rule
//	PowerMockRule rule = new PowerMockRule();
//	static {PowerMockAgent.initializeIfNeeded();}

	@Test
	public void testHoge() throws Exception {
		var  app = new App();
		var mock = PowerMockito.mock(FunctionHoge.class);
		PowerMockito.whenNew(FunctionHoge.class).withNoArguments().thenReturn(mock);
		String toBeReturned = "ほげ";
		PowerMockito.doReturn(toBeReturned).when(mock).hoge();
		var result = app.hoge();
		assertEquals(toBeReturned, result);

	}
}
