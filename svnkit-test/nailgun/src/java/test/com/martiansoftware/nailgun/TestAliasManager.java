/*   

  Copyright 2004, Martian Software, Inc.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.

*/

package com.martiansoftware.nailgun;

import java.util.Iterator;
import java.util.Set;

import junit.framework.TestCase;

/**
 * 
 * @author <a href="http://www.martiansoftware.com/contact.html">Marty Lamb</a>
 */
public class TestAliasManager extends TestCase {

	public void testAliasManager() {
		AliasManager amgr = new AliasManager();
		Set aliases = amgr.getAliases();
		assertFalse(0 == aliases.size());
		for (Iterator i = aliases.iterator(); i.hasNext();) {
			Alias alias = (Alias) i.next();
			assertEquals(alias, amgr.getAlias(alias.getName()));
			amgr.removeAlias(alias.getName());
			assertNull(amgr.getAlias(alias.getName()));
		}
		aliases = amgr.getAliases();
		assertEquals(0, aliases.size());
	}
}
