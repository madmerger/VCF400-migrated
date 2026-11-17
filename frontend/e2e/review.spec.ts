import { test, expect, type Page } from '@playwright/test';
const backend=`http://127.0.0.1:${process.env.VCF_BACKEND_PORT||8080}`;
test.beforeEach(async({page,request})=>{
  expect((await request.post(`${backend}/api/e2e/reset`,{headers:{'X-E2E-Key':process.env.VCF_E2E_KEY!},data:{}})).ok()).toBeTruthy();
  await page.goto('/');await expect(page.getByTestId('menu-11')).toBeVisible();
});
async function authenticate(page:Page){
  await page.getByTestId('nav-kiosk').click();await page.getByTestId('kiosk-selection').fill('7');
  await page.getByRole('button',{name:'選択',exact:true}).click();
  await page.getByTestId('password').fill(process.env.VCF_SEED_ADMPSWRD!);
  const response=page.waitForResponse(r=>r.url().endsWith('/admin/auth'));
  await page.getByTestId('auth-submit').click();
  const token=(await (await response).json()).token as string;
  await expect(page.getByTestId('toggle-voting')).toBeVisible();return token;
}
for(const exit of ['signoff','F3'])test(`Admin ${exit} revokes token and removes next visitor privileges`,async({page,request})=>{
  const token=await authenticate(page);
  if(exit==='signoff'){
    await page.getByTestId('nav-menu').click();await page.getByTestId('menu-80').click();
    await expect(page.getByTestId('screen-title')).toHaveText('Sign Off the System');
  }else {await page.keyboard.press('F3');await expect(page.getByTestId('kiosk-selection')).toBeVisible();}
  await page.getByTestId('nav-settings').click();await expect(page.getByTestId('voting-status')).toBeVisible();
  await expect(page.getByTestId('toggle-voting')).toHaveCount(0);
  const response=await request.patch(`${backend}/api/settings`,{headers:{Authorization:`Bearer ${token}`},data:{ALWVOTE:'N'}});
  expect(response.status()).toBe(401);
});

test('LEARN EOF retains cursor for F8 and restores forward navigation',async({page})=>{
  await page.goto('/?page=8&collection=global');await expect(page.getByTestId('page-number')).toHaveText('PAGE 08');
  await page.getByTestId('key-F5').click();await expect(page.getByTestId('learn-eof')).toBeVisible();
  await expect(page.getByTestId('page-number')).toHaveText('PAGE 08');
  await page.keyboard.press('F8');await expect(page.getByTestId('page-number')).toHaveText('PAGE 07');
  await expect(page.getByTestId('error-row')).toHaveText('');await expect(page.getByTestId('key-F5')).toBeEnabled();
});
