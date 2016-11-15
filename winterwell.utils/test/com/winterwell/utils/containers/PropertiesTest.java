package winterwell.utils.containers;

import org.junit.Test;

import winterwell.utils.web.XStreamUtils;

public class PropertiesTest {

	@Test
	public void testXMLFail() {
		String xml = "<props class='winterwell.utils.containers.Properties'><properties/></props>";
		Object props = XStreamUtils.serialiseFromXml(xml);
		System.out.println(props);
	}
}
