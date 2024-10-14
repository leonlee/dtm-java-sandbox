/*
 * Copyright 2021 Gypsophila open source organization.
 *
 * Licensed under the Apache License,Version2.0(the"License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dtmcli.java.sample.param;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2024年10月11日 上午11:26:09
 * @Desc 些年若许,不负芳华.
 *
 */
public record TransNotifyReq(
		String transId,
		int transOutUserId,
		int transInUserId,
		int amount,
		String transTime) {
	
}
