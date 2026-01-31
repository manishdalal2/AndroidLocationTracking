import { SplashScreen } from '@capacitor/splash-screen';
import { Camera } from '@capacitor/camera';
import { registerPlugin } from '@capacitor/core';

// Register the native LocationTracker plugin
const LocationTracker = registerPlugin('LocationTracker');

const SERVER_ENDPOINT = 'http://192.168.1.155:3000';

window.customElements.define(
  'capacitor-welcome',
  class extends HTMLElement {
    constructor() {
      super();

      SplashScreen.hide();

      const root = this.attachShadow({ mode: 'open' });

      root.innerHTML = `
    <style>

    </style>
    <div>
      <capacitor-welcome-titlebar>
        <h1>Welcome to Capacitor1</h1>
    </div>
    `;
    }

    connectedCallback() {
      const self = this;

      // Start native location tracking service
      this.startNativeLocationTracking();

      self.shadowRoot.querySelector('#take-photo')?.addEventListener('click', async function (e) {
        try {
          const photo = await Camera.getPhoto({
            resultType: 'uri',
          });

          const image = self.shadowRoot.querySelector('#image');
          if (!image) {
            return;
          }

          image.src = photo.webPath;
        } catch (e) {
          console.warn('User cancelled', e);
        }
      });
    }

    async startNativeLocationTracking() {
      try {
        // Get access token (replace with actual token retrieval logic)
        const accessToken = localStorage.getItem('accessToken') || '';
        
        const result = await LocationTracker.startTracking({
          endpoint: SERVER_ENDPOINT,
          accessToken: accessToken,
        });
        console.log('Native location tracking:', result.message);
      } catch (error) {
        console.error('Failed to start native location tracking:', error);
      }
    }

    async updateAccessToken(newAccessToken) {
      try {
        // Store the new token
        localStorage.setItem('accessToken', newAccessToken);
        
        // Update the token in native plugin
        const result = await LocationTracker.updateToken({
          accessToken: newAccessToken,
        });
        console.log('Access token updated:', result.message);
      } catch (error) {
        console.error('Failed to update access token:', error);
      }
    }

    async stopNativeLocationTracking() {
      try {
        const result = await LocationTracker.stopTracking();
        console.log('Location tracking stopped:', result.message);
      } catch (error) {
        console.error('Failed to stop location tracking:', error);
      }
    }
  },
);

window.customElements.define(
  'capacitor-welcome-titlebar',
  class extends HTMLElement {
    constructor() {
      super();
      const root = this.attachShadow({ mode: 'open' });
      root.innerHTML = `
    <style>
      :host {
        position: relative;
        display: block;
        padding: 15px 15px 15px 15px;
        text-align: center;
        background-color: #73B5F6;
      }
      ::slotted(h1) {
        margin: 0;
        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif, "Apple Color Emoji", "Segoe UI Emoji", "Segoe UI Symbol";
        font-size: 0.9em;
        font-weight: 600;
        color: #fff;
      }
    </style>
    <slot></slot>
    `;
    }
  },
);
