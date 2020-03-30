/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.compatibility.targetprep;

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.targetprep.TestAppInstallSetup;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertThrows;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;

@RunWith(JUnit4.class)
public final class AppSetupPreparerTest {

    private static final String OPTION_GCS_APK_DIR = "gcs-apk-dir";
    private static final String OPTION_CHECK_DEVICE_AVAILABLE = "check-device-available";
    private static final String OPTION_MAX_RETRY = "max-retry";
    private static final String OPTION_EXPONENTIAL_BACKOFF_MULTIPLIER_SECONDS =
            "exponential-backoff-multiplier-seconds";
    private static final ITestDevice NULL_DEVICE = null;
    private static final IBuildInfo NULL_BUILD_INFO = null;
    private static final String NULL_PACKAGE_NAME = null;
    private static final TestAppInstallSetup NULL_TEST_APP_INSTALL_SETUP = null;
    private static final String TEST_PACKAGE_NAME = "test.package.name";

    @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

    private final IBuildInfo mBuildInfo = new BuildInfo();
    private final TestAppInstallSetup mMockAppInstallSetup = mock(TestAppInstallSetup.class);
    private FakeSleeper mFakeSleeper;
    private Configuration mConfiguration = new Configuration(null, null);

    @Test
    public void setUp_gcsApkDirIsNull_throwsException()
            throws DeviceNotAvailableException, TargetSetupError {
        AppSetupPreparer preparer = createPreparer();
        mBuildInfo.addBuildAttribute(OPTION_GCS_APK_DIR, null);

        assertThrows(NullPointerException.class, () -> preparer.setUp(NULL_DEVICE, mBuildInfo));
    }

    @Test
    public void setUp_gcsApkDirIsNotDir_throwsException()
            throws IOException, DeviceNotAvailableException, TargetSetupError {
        AppSetupPreparer preparer = createPreparer();
        File tempFile = tempFolder.newFile("temp_file_name");
        mBuildInfo.addBuildAttribute(OPTION_GCS_APK_DIR, tempFile.getPath());

        assertThrows(IllegalArgumentException.class, () -> preparer.setUp(NULL_DEVICE, mBuildInfo));
    }

    @Test
    public void setUp_packageDirDoesNotExist_throwsError()
            throws IOException, DeviceNotAvailableException, TargetSetupError {
        AppSetupPreparer preparer = createPreparer();
        File gcsApkDir = tempFolder.newFolder("gcs_apk_dir");
        mBuildInfo.addBuildAttribute(OPTION_GCS_APK_DIR, gcsApkDir.getPath());

        assertThrows(IllegalArgumentException.class, () -> preparer.setUp(NULL_DEVICE, mBuildInfo));
    }

    @Test
    public void setUp_apkDoesNotExist() throws Exception {
        AppSetupPreparer preparer = createPreparer();
        File gcsApkDir = tempFolder.newFolder("gcs_apk_dir");
        createPackageFile(gcsApkDir, TEST_PACKAGE_NAME, "non_apk_file");
        mBuildInfo.addBuildAttribute(OPTION_GCS_APK_DIR, gcsApkDir.getPath());

        assertThrows(TargetSetupError.class, () -> preparer.setUp(NULL_DEVICE, mBuildInfo));
    }

    @Test
    public void setUp_installSplitApk() throws Exception {
        AppSetupPreparer preparer = createPreparer();
        File gcsApkDir = tempFolder.newFolder("gcs_apk_dir");
        File packageDir = new File(gcsApkDir.getPath(), TEST_PACKAGE_NAME);
        createPackageFile(gcsApkDir, TEST_PACKAGE_NAME, "apk_name_1.apk");
        createPackageFile(gcsApkDir, TEST_PACKAGE_NAME, "apk_name_2.apk");
        mBuildInfo.addBuildAttribute(OPTION_GCS_APK_DIR, gcsApkDir.getPath());

        preparer.setUp(NULL_DEVICE, mBuildInfo);

        verify(mMockAppInstallSetup).setAltDir(packageDir);
        verify(mMockAppInstallSetup)
                .addSplitApkFileNames(
                        argThat(s -> s.contains("apk_name_1.apk") && s.contains("apk_name_2.apk")));
        verify(mMockAppInstallSetup).setUp(any(), any());
    }

    @Test
    public void setUp_installNonSplitApk() throws Exception {
        AppSetupPreparer preparer = createPreparer();
        File gcsApkDir = tempFolder.newFolder("gcs_apk_dir");
        File packageDir = new File(gcsApkDir.getPath(), TEST_PACKAGE_NAME);
        createPackageFile(gcsApkDir, TEST_PACKAGE_NAME, "apk_name_1.apk");
        mBuildInfo.addBuildAttribute(OPTION_GCS_APK_DIR, gcsApkDir.getPath());

        preparer.setUp(NULL_DEVICE, mBuildInfo);

        verify(mMockAppInstallSetup).setAltDir(packageDir);
        verify(mMockAppInstallSetup).addTestFileName("apk_name_1.apk");
        verify(mMockAppInstallSetup).setUp(any(), any());
    }

    @Test
    public void tearDown() throws Exception {
        AppSetupPreparer preparer = createPreparer();
        TestInformation testInfo = TestInformation.newBuilder().build();

        preparer.tearDown(testInfo, null);

        verify(mMockAppInstallSetup, times(1)).tearDown(testInfo, null);
    }

    @Test
    public void setUp_withinRetryLimit_doesNotThrowException() throws Exception {
        AppSetupPreparer preparer = createPreparer();
        IBuildInfo buildInfo = createValidBuildInfo();
        setPreparerOption(preparer, OPTION_MAX_RETRY, "1");
        doThrow(new TargetSetupError("Still failing"))
                .doNothing()
                .when(mMockAppInstallSetup)
                .setUp(any(), any());

        preparer.setUp(NULL_DEVICE, buildInfo);
    }

    @Test
    public void setUp_exceedsRetryLimit_throwException() throws Exception {
        AppSetupPreparer preparer = createPreparer();
        IBuildInfo buildInfo = createValidBuildInfo();
        setPreparerOption(preparer, OPTION_MAX_RETRY, "1");
        doThrow(new TargetSetupError("Still failing"))
                .doThrow(new TargetSetupError("Still failing"))
                .doNothing()
                .when(mMockAppInstallSetup)
                .setUp(any(), any());

        assertThrows(TargetSetupError.class, () -> preparer.setUp(NULL_DEVICE, buildInfo));
    }

    @Test
    public void setUp_zeroMaxRetry_runsOnce() throws Exception {
        AppSetupPreparer preparer = createPreparer();
        IBuildInfo buildInfo = createValidBuildInfo();
        setPreparerOption(preparer, OPTION_MAX_RETRY, "0");
        doNothing().when(mMockAppInstallSetup).setUp(any(), any());

        preparer.setUp(NULL_DEVICE, buildInfo);

        verify(mMockAppInstallSetup, times(1)).setUp(any(), any());
    }

    @Test
    public void setUp_positiveMaxRetryButNoException_runsOnlyOnce() throws Exception {
        AppSetupPreparer preparer = createPreparer();
        IBuildInfo buildInfo = createValidBuildInfo();
        setPreparerOption(preparer, OPTION_MAX_RETRY, "1");
        doNothing().when(mMockAppInstallSetup).setUp(any(), any());

        preparer.setUp(NULL_DEVICE, buildInfo);

        verify(mMockAppInstallSetup, times(1)).setUp(any(), any());
    }

    @Test
    public void setUp_negativeMaxRetry_throwsException() throws Exception {
        AppSetupPreparer preparer = createPreparer();
        IBuildInfo buildInfo = createValidBuildInfo();
        setPreparerOption(preparer, OPTION_MAX_RETRY, "-1");

        assertThrows(IllegalArgumentException.class, () -> preparer.setUp(NULL_DEVICE, buildInfo));
    }

    @Test
    public void setUp_deviceDisconnectedAndCheckDeviceAvailable_throwsDeviceNotAvailableException()
            throws Exception {
        AppSetupPreparer preparer = createPreparer();
        IBuildInfo buildInfo = createValidBuildInfo();
        setPreparerOption(preparer, OPTION_CHECK_DEVICE_AVAILABLE, "true");
        makeInstallerThrow(new TargetSetupError("Connection reset by peer."));

        assertThrows(
                DeviceNotAvailableException.class,
                () -> preparer.setUp(createUnavailableDevice(), buildInfo));
    }

    @Test
    public void setUp_deviceConnectedAndCheckDeviceAvailable_doesNotChangeException()
            throws Exception {
        AppSetupPreparer preparer = createPreparer();
        IBuildInfo buildInfo = createValidBuildInfo();
        setPreparerOption(preparer, OPTION_CHECK_DEVICE_AVAILABLE, "true");
        makeInstallerThrow(new TargetSetupError("Connection reset by peer."));

        assertThrows(
                TargetSetupError.class, () -> preparer.setUp(createAvailableDevice(), buildInfo));
    }

    @Test
    public void setUp_deviceDisconnectedAndNotCheckDeviceAvailable_doesNotChangeException()
            throws Exception {
        AppSetupPreparer preparer = createPreparer();
        IBuildInfo buildInfo = createValidBuildInfo();
        setPreparerOption(preparer, OPTION_CHECK_DEVICE_AVAILABLE, "false");
        makeInstallerThrow(new TargetSetupError("Connection reset by peer."));

        assertThrows(
                TargetSetupError.class, () -> preparer.setUp(createUnavailableDevice(), buildInfo));
    }

    @Test
    public void setUp_negativeExponentialBackoffMultiplier_throwsIllegalArgumentException()
            throws Exception {
        AppSetupPreparer preparer = createPreparer();
        IBuildInfo buildInfo = createValidBuildInfo();
        setPreparerOption(preparer, OPTION_EXPONENTIAL_BACKOFF_MULTIPLIER_SECONDS, "-1");

        assertThrows(IllegalArgumentException.class, () -> preparer.setUp(NULL_DEVICE, buildInfo));
    }

    @Test
    public void setUp_testFileNameOptionSet_forwardsToUnderlyingPreparer() throws Exception {
        AppSetupPreparer preparer = createPreparer();
        IBuildInfo buildInfo = createValidBuildInfo();
        new OptionSetter(preparer).setOptionValue("test-file-name", "additional.apk");

        preparer.setUp(NULL_DEVICE, buildInfo);

        verify(mMockAppInstallSetup).addTestFileName("additional.apk");
    }

    @Test
    public void setUp_zeroExponentialBackoffMultiplier_noSleepBetweenRetries() throws Exception {
        AppSetupPreparer preparer = createPreparer();
        IBuildInfo buildInfo = createValidBuildInfo();
        setPreparerOption(preparer, OPTION_EXPONENTIAL_BACKOFF_MULTIPLIER_SECONDS, "0");
        setPreparerOption(preparer, OPTION_MAX_RETRY, "1");
        makeInstallerThrow(new TargetSetupError(""));

        assertThrows(TargetSetupError.class, () -> preparer.setUp(NULL_DEVICE, buildInfo));
        assertThat(mFakeSleeper.getSleepHistory().get(0)).isEqualTo(Duration.ofSeconds(0));
    }

    @Test
    public void setUp_positiveExponentialBackoffMultiplier_sleepsBetweenRetries() throws Exception {
        AppSetupPreparer preparer = createPreparer();
        IBuildInfo buildInfo = createValidBuildInfo();
        setPreparerOption(preparer, OPTION_EXPONENTIAL_BACKOFF_MULTIPLIER_SECONDS, "3");
        setPreparerOption(preparer, OPTION_MAX_RETRY, "3");
        makeInstallerThrow(new TargetSetupError(""));

        assertThrows(TargetSetupError.class, () -> preparer.setUp(NULL_DEVICE, buildInfo));
        assertThat(mFakeSleeper.getSleepHistory().get(0)).isEqualTo(Duration.ofSeconds(3));
        assertThat(mFakeSleeper.getSleepHistory().get(1)).isEqualTo(Duration.ofSeconds(9));
        assertThat(mFakeSleeper.getSleepHistory().get(2)).isEqualTo(Duration.ofSeconds(27));
    }

    @Test
    public void setUp_interruptedDuringBackoff_throwsException() throws Exception {
        FakeSleeper sleeper = new FakeThrowingSleeper();
        AppSetupPreparer preparer = createPreparerWithSleeper(sleeper);
        IBuildInfo buildInfo = createValidBuildInfo();
        setPreparerOption(preparer, OPTION_EXPONENTIAL_BACKOFF_MULTIPLIER_SECONDS, "3");
        setPreparerOption(preparer, OPTION_MAX_RETRY, "3");
        makeInstallerThrow(new TargetSetupError(""));

        assertThrows(RuntimeException.class, () -> preparer.setUp(NULL_DEVICE, buildInfo));
        assertThat(sleeper.getSleepHistory().size()).isEqualTo(1);
    }

    private void setPreparerOption(AppSetupPreparer preparer, String key, String val)
            throws Exception {
        new OptionSetter(preparer).setOptionValue(key, val);
    }

    private void makeInstallerThrow(Exception e) throws Exception {
        doThrow(e).when(mMockAppInstallSetup).setUp(any(), any());
    }

    private IBuildInfo createValidBuildInfo() throws Exception {
        IBuildInfo buildInfo = new BuildInfo();
        File gcsApkDir = tempFolder.newFolder("any");
        File packageDir = new File(gcsApkDir.getPath(), TEST_PACKAGE_NAME);
        createPackageFile(gcsApkDir, TEST_PACKAGE_NAME, "test.apk");
        buildInfo.addBuildAttribute(OPTION_GCS_APK_DIR, gcsApkDir.getPath());
        return buildInfo;
    }

    private static ITestDevice createUnavailableDevice() throws Exception {
        ITestDevice device = mock(ITestDevice.class);
        when(device.getProperty(any())).thenReturn(null);
        return device;
    }

    private static ITestDevice createAvailableDevice() throws Exception {
        ITestDevice device = mock(ITestDevice.class);
        when(device.getProperty(any())).thenReturn("");
        return device;
    }

    private static File createPackageFile(File parentDir, String packageName, String apkName)
            throws IOException {
        File packageDir =
                Files.createDirectories(Paths.get(parentDir.getAbsolutePath(), packageName))
                        .toFile();

        return Files.createFile(Paths.get(packageDir.getAbsolutePath(), apkName)).toFile();
    }

    private static class FakeSleeper implements AppSetupPreparer.Sleeper {
        private ArrayList<Duration> mSleepHistory = new ArrayList<>();

        @Override
        public void sleep(Duration duration) throws InterruptedException {
            mSleepHistory.add(duration);
        }

        ArrayList<Duration> getSleepHistory() {
            return mSleepHistory;
        }
    }

    private static class FakeThrowingSleeper extends FakeSleeper {
        @Override
        public void sleep(Duration duration) throws InterruptedException {
            super.sleep(duration);
            throw new InterruptedException("_");
        }
    }

    private AppSetupPreparer createPreparer() {
        mFakeSleeper = new FakeSleeper();
        return createPreparerWithSleeper(mFakeSleeper);
    }

    private AppSetupPreparer createPreparerWithSleeper(FakeSleeper sleeper) {
        return new AppSetupPreparer(TEST_PACKAGE_NAME, mMockAppInstallSetup, sleeper);
    }
}
