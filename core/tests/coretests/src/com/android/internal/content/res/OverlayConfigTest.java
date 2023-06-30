/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.internal.content.res;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.pm.PackagePartitions;
import android.os.FileUtils;
import android.os.SystemProperties;
import android.platform.test.annotations.Presubmit;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.frameworks.coretests.R;
import com.android.internal.content.om.OverlayConfig;
import com.android.internal.content.om.OverlayConfig.IdmapInvocation;
import com.android.internal.content.om.OverlayConfigParser.OverlayPartition;
import com.android.internal.content.om.OverlayScanner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Presubmit
@RunWith(AndroidJUnit4.class)
public class OverlayConfigTest {
    private static final String TEST_APK_PACKAGE_NAME =
            "com.android.frameworks.coretests.overlay_config";

    private ExpectedException mExpectedException = ExpectedException.none();
    private OverlayConfigIterationRule mScannerRule = new OverlayConfigIterationRule();
    private TemporaryFolder mTestFolder = new TemporaryFolder();

    @Rule
    public RuleChain chain = RuleChain.outerRule(mExpectedException)
            .around(mTestFolder).around(mScannerRule);

    private OverlayConfig createConfigImpl() throws IOException {
        return new OverlayConfig(mTestFolder.getRoot().getCanonicalFile(),
                mScannerRule.getScannerFactory(), mScannerRule.getPackageProvider());
    }

    private File createFile(String fileName) throws IOException {
        return createFile(fileName, "");
    }

    private File createFile(String fileName, String content) throws IOException {
        final File f = new File(String.format("%s/%s", mTestFolder.getRoot(), fileName));
        if (!f.getParentFile().equals(mTestFolder.getRoot())) {
            f.getParentFile().mkdirs();
        }
        FileUtils.stringToFile(f.getPath(), content);
        return f;
    }

    private static void assertConfig(OverlayConfig overlayConfig, String packageName,
            boolean mutable, boolean enabled, int configIndex) {
        final OverlayConfig.Configuration config = overlayConfig.getConfiguration(packageName);
        assertNotNull(config);
        assertEquals(mutable, config.parsedConfig.mutable);
        assertEquals(enabled, config.parsedConfig.enabled);
        assertEquals(configIndex, config.configIndex);
    }

    private String generatePartitionOrderString(List<OverlayPartition> partitions) {
        StringBuilder partitionOrder = new StringBuilder();
        for (int i = 0; i < partitions.size(); i++) {
            partitionOrder.append(partitions.get(i).getName());
            if (i < partitions.size() - 1) {
                partitionOrder.append(", ");
            }
        }
        return partitionOrder.toString();
    }

    // configIndex should come from real time partition order cause partitions could get
    // reordered by /product/overlay/partition_order.xml
    private Map<String, Integer> createConfigIndexes(OverlayConfig overlayConfig,
            String... configPartitions) {
        Map<String, Integer> configIndexes = new HashMap<>();
        for (int i = 0; i < configPartitions.length; i++) {
            configIndexes.put(configPartitions[i], -1);
        }

        String[] partitions = overlayConfig.getPartitionOrder().split(", ");
        int index = 0;
        for (int i = 0; i < partitions.length; i++) {
            if (configIndexes.containsKey(partitions[i])) {
                configIndexes.put(partitions[i], index++);
            }
        }
        return configIndexes;
    }

    @Test
    public void testSortPartitionsWithoutXml() throws IOException {
        ArrayList<OverlayPartition> partitions = new ArrayList<>(
                PackagePartitions.getOrderedPartitions(OverlayPartition::new));

        final OverlayConfig overlayConfig = createConfigImpl();
        String partitionOrderFilePath = String.format("%s/%s", mTestFolder.getRoot(),
                "/product/overlay/partition_order.xml");
        assertEquals(false, overlayConfig.sortPartitions(partitionOrderFilePath, partitions));
        assertEquals("system, vendor, odm, oem, product, system_ext",
                generatePartitionOrderString(partitions));
    }

    @Test
    public void testSortPartitionsWithInvalidXmlRootElement() throws IOException {
        ArrayList<OverlayPartition> partitions = new ArrayList<>(
                PackagePartitions.getOrderedPartitions(OverlayPartition::new));
        createFile("/product/overlay/partition_order.xml",
                "<partition-list>\n"
                        + "  <partition name=\"system_ext\"/>\n"
                        + "  <partition name=\"vendor\"/>\n"
                        + "  <partition name=\"oem\"/>\n"
                        + "  <partition name=\"odm\"/>\n"
                        + "  <partition name=\"product\"/>\n"
                        + "  <partition name=\"system\"/>\n"
                        + "</partition-list>\n");
        final OverlayConfig overlayConfig = createConfigImpl();
        String partitionOrderFilePath = String.format("%s/%s", mTestFolder.getRoot(),
                "/product/overlay/partition_order.xml");
        assertEquals(false, overlayConfig.sortPartitions(partitionOrderFilePath, partitions));
        assertEquals("system, vendor, odm, oem, product, system_ext",
                generatePartitionOrderString(partitions));
    }

    @Test
    public void testSortPartitionsWithInvalidPartition() throws IOException {
        ArrayList<OverlayPartition> partitions = new ArrayList<>(
                PackagePartitions.getOrderedPartitions(OverlayPartition::new));
        createFile("/product/overlay/partition_order.xml",
                "<partition-order>\n"
                        + "  <partition name=\"INVALID\"/>\n"
                        + "  <partition name=\"vendor\"/>\n"
                        + "  <partition name=\"oem\"/>\n"
                        + "  <partition name=\"odm\"/>\n"
                        + "  <partition name=\"product\"/>\n"
                        + "  <partition name=\"system\"/>\n"
                        + "</partition-order>\n");
        final OverlayConfig overlayConfig = createConfigImpl();
        String partitionOrderFilePath = String.format("%s/%s", mTestFolder.getRoot(),
                "/product/overlay/partition_order.xml");
        assertEquals(false, overlayConfig.sortPartitions(partitionOrderFilePath, partitions));
        assertEquals("system, vendor, odm, oem, product, system_ext",
                generatePartitionOrderString(partitions));
    }

    @Test
    public void testSortPartitionsWithDuplicatePartition() throws IOException {
        ArrayList<OverlayPartition> partitions = new ArrayList<>(
                PackagePartitions.getOrderedPartitions(OverlayPartition::new));
        createFile("/product/overlay/partition_order.xml",
                "<partition-order>\n"
                        + "  <partition name=\"system_ext\"/>\n"
                        + "  <partition name=\"system\"/>\n"
                        + "  <partition name=\"vendor\"/>\n"
                        + "  <partition name=\"oem\"/>\n"
                        + "  <partition name=\"odm\"/>\n"
                        + "  <partition name=\"product\"/>\n"
                        + "  <partition name=\"system\"/>\n"
                        + "</partition-order>\n");
        final OverlayConfig overlayConfig = createConfigImpl();
        String partitionOrderFilePath = String.format("%s/%s", mTestFolder.getRoot(),
                "/product/overlay/partition_order.xml");
        assertEquals(false, overlayConfig.sortPartitions(partitionOrderFilePath, partitions));
        assertEquals("system, vendor, odm, oem, product, system_ext",
                generatePartitionOrderString(partitions));
    }

    @Test
    public void testSortPartitionsWithMissingPartition() throws IOException {
        ArrayList<OverlayPartition> partitions = new ArrayList<>(
                PackagePartitions.getOrderedPartitions(OverlayPartition::new));
        createFile("/product/overlay/partition_order.xml",
                "<partition-order>\n"
                        + "  <partition name=\"vendor\"/>\n"
                        + "  <partition name=\"oem\"/>\n"
                        + "  <partition name=\"odm\"/>\n"
                        + "  <partition name=\"product\"/>\n"
                        + "  <partition name=\"system\"/>\n"
                        + "</partition-order>\n");
        final OverlayConfig overlayConfig = createConfigImpl();
        String partitionOrderFilePath = String.format("%s/%s", mTestFolder.getRoot(),
                "/product/overlay/partition_order.xml");
        assertEquals(false, overlayConfig.sortPartitions(partitionOrderFilePath, partitions));
        assertEquals("system, vendor, odm, oem, product, system_ext",
                generatePartitionOrderString(partitions));
    }

    @Test
    public void testSortPartitionsWithCorrectPartitionOrderXml() throws IOException {
        ArrayList<OverlayPartition> partitions = new ArrayList<>(
                PackagePartitions.getOrderedPartitions(OverlayPartition::new));
        createFile("/product/overlay/partition_order.xml",
                "<partition-order>\n"
                        + "  <partition name=\"system_ext\"/>\n"
                        + "  <partition name=\"vendor\"/>\n"
                        + "  <partition name=\"oem\"/>\n"
                        + "  <partition name=\"odm\"/>\n"
                        + "  <partition name=\"product\"/>\n"
                        + "  <partition name=\"system\"/>\n"
                        + "</partition-order>\n");
        final OverlayConfig overlayConfig = createConfigImpl();
        String partitionOrderFilePath = String.format("%s/%s", mTestFolder.getRoot(),
                "/product/overlay/partition_order.xml");
        assertEquals(true, overlayConfig.sortPartitions(partitionOrderFilePath, partitions));
        assertEquals("system_ext, vendor, oem, odm, product, system",
                generatePartitionOrderString(partitions));
    }
}
