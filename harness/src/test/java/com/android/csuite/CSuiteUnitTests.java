/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.csuite;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses({
    com.android.compatibility.targetprep.AppSetupPreparerTest.class,
    com.android.compatibility.targetprep.CheckGmsPreparerTest.class,
    com.android.compatibility.targetprep.SystemAppRemovalPreparerTest.class,
    com.android.compatibility.testtype.AppLaunchTestTest.class,
    com.android.csuite.config.AppRemoteFileResolverTest.class,
    com.android.csuite.core.GenerateModulePreparerTest.class,
    com.android.csuite.testing.CorrespondencesTest.class,
    com.android.csuite.testing.MoreAssertsTest.class,
})
public final class CSuiteUnitTests {
    // Intentionally empty.
}