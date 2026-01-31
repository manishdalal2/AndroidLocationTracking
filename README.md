## Created with Capacitor Create App


 1. Run npx cap add android (creates fresh folder)   npx cap add android                                                                                                            
  2. Re-apply your native code from git: git checkout -- android/app/src/main/java/                                                                            
  3. Re-apply manifest changes: git checkout -- android/app/src/main/AndroidManifest.xml    


  npm run build && npx cap sync android 



This app was created using [`@capacitor/create-app`](https://github.com/ionic-team/create-capacitor-app),
and comes with a very minimal shell for building an app.

### Running this example

To run the provided example, you can use `npm start` command.

```bash
nvm use 24
npm install
npx cap add android                                                         
npm run build && npx cap sync android 
```


git remote set-url origin git@github-personal:manishdalal2/AndroidLocationTracking.git