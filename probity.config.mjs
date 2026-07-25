import { defineConfig, enforceTdd } from '@nizos/probity'

export default defineConfig({ rules: [{ files: ['**/src/main/java/**'], rules: [enforceTdd()] }] })
