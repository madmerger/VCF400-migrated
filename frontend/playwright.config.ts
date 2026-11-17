import { defineConfig } from '@playwright/test';
import { randomBytes } from 'node:crypto';
import { existsSync, mkdirSync } from 'node:fs';
import { homedir } from 'node:os';
process.env.VCF_SEED_ADMPSWRD ||= randomBytes(5).toString('hex').slice(0,9);
process.env.VCF_E2E_KEY ||= randomBytes(32).toString('hex');
const cached = `${homedir()}/.cache/ms-playwright/chromium-1255/chrome-linux64/chrome`;
mkdirSync('.e2e',{recursive:true});
export default defineConfig({
  testDir:'./e2e', workers:1, fullyParallel:false, retries:0, timeout:30_000,
  outputDir:'test-results/artifacts', reporter:[['list'],['./e2e/reporter.ts']],
  use:{baseURL:'http://127.0.0.1:5173',viewport:{width:1280,height:720},video:{mode:'on',size:{width:1280,height:720}},trace:'off',screenshot:'only-on-failure',launchOptions:{executablePath:process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE||(existsSync(cached)?cached:undefined)}},
  webServer:[
    {command:'mvn -s maven-settings-gcs.xml -q spring-boot:run -Dspring-boot.run.profiles=e2e > ../frontend/.e2e/backend.log 2>&1',cwd:'../backend',url:'http://127.0.0.1:8080/api/menu',timeout:180_000,reuseExistingServer:false},
    {command:'npm run dev -- --port 5173 --strictPort',url:'http://127.0.0.1:5173',reuseExistingServer:false}
  ]
});
