import { defineConfig } from '@playwright/test';
import { randomBytes } from 'node:crypto';
import { existsSync, mkdirSync } from 'node:fs';
import { homedir } from 'node:os';
process.env.VCF_SEED_ADMPSWRD ||= randomBytes(5).toString('hex').slice(0,9);
process.env.VCF_E2E_KEY ||= randomBytes(32).toString('hex');
const cached = `${homedir()}/.cache/ms-playwright/chromium-1255/chrome-linux64/chrome`;
mkdirSync('.e2e',{recursive:true});
const backendPort=Number(process.env.VCF_BACKEND_PORT||8080);
const webPort=Number(process.env.VCF_WEB_PORT||5173);
export default defineConfig({
  testDir:'./e2e', workers:1, fullyParallel:false, retries:0, timeout:30_000,
  outputDir:'test-results/artifacts', reporter:[['list'],['./e2e/reporter.ts']],
  use:{baseURL:`http://127.0.0.1:${webPort}`,viewport:{width:1280,height:720},video:{mode:'on',size:{width:1280,height:720}},trace:'off',screenshot:'only-on-failure',launchOptions:{executablePath:process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE||(existsSync(cached)?cached:undefined)}},
  webServer:[
    {command:`mvn -s maven-settings-gcs.xml -q spring-boot:run -Dspring-boot.run.profiles=e2e -Dspring-boot.run.arguments=--server.port=${backendPort} > ../frontend/.e2e/backend.log 2>&1`,cwd:'../backend',url:`http://127.0.0.1:${backendPort}/api/menu`,timeout:180_000,reuseExistingServer:false},
    {command:`npm run dev -- --port ${webPort} --strictPort`,url:`http://127.0.0.1:${webPort}`,reuseExistingServer:false}
  ]
});
