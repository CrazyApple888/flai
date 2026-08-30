package me.drew.flai.infrastructure.pipeline

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PipelineFileNamesTest {

    @Test
    fun `pipeline integrations accept supported YAML file names`() {
        assertTrue(isPipelineFileName("review.flai"))
        assertTrue(isPipelineFileName("review.flai.yaml"))
        assertTrue(isPipelineFileName("review.flai.yml"))
        assertTrue(isPipelineFileName("review.yaml"))
        assertTrue(isPipelineFileName("review.yml"))
    }

    @Test
    fun `pipeline discovery rejects lookalike file names`() {
        assertFalse(isPipelineFileName("review.flai.txt"))
        assertFalse(isPipelineFileName("review.yaml.bak"))
        assertFalse(isPipelineFileName("review.yml.bak"))
    }
}
