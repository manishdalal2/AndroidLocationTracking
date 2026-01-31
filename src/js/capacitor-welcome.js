import { SplashScreen } from '@capacitor/splash-screen';
import { Camera } from '@capacitor/camera';
import { Geolocation } from '@capacitor/geolocation';

window.customElements.define(
  'capacitor-welcome',
  class extends HTMLElement {
    constructor() {
      super();

      SplashScreen.hide();

      const root = this.attachShadow({ mode: 'open' });

      root.innerHTML = `
    <style>
      :host {
        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif, "Apple Color Emoji", "Segoe UI Emoji", "Segoe UI Symbol";
        display: block;
        width: 100%;
        height: 100%;
      }
      h1, h2, h3, h4, h5 {
        text-transform: uppercase;
      }
      .button {
        display: inline-block;
        padding: 10px;
        background-color: #73B5F6;
        color: #fff;
        font-size: 0.9em;
        border: 0;
        border-radius: 3px;
        text-decoration: none;
        cursor: pointer;
      }
      main {
        padding: 15px;
      }
      main hr { height: 1px; background-color: #eee; border: 0; }
      main h1 {
        font-size: 1.4em;
        text-transform: uppercase;
        letter-spacing: 1px;
      }
      main h2 {
        font-size: 1.1em;
      }
      main h3 {
        font-size: 0.9em;
      }
      main p {
        color: #333;
      }
      main pre {
        white-space: pre-line;
      }
    </style>
    <div>
      <capacitor-welcome-titlebar>
        <h1>Capacitor</h1>
      </capacitor-welcome-titlebar>
      <main>
        <p>
          Capacitor makes it easy to build powerful apps for the app stores, mobile web (Progressive Web Apps), and desktop, all
          with a single code base.
        </p>
        <h2>Getting Started</h2>
        <p>
          You'll probably need a UI framework to build a full-featured app. Might we recommend
          <a target="_blank" href="http://ionicframework.com/">Ionic</a>?
        </p>
        <p>
          Visit <a href="https://capacitorjs.com">capacitorjs.com</a> for information
          on using native features, building plugins, and more.
        </p>
        <a href="https://capacitorjs.com" target="_blank" class="button">Read more</a>
        <h2>Tiny Demo</h2>
        <p>
          This demo shows how to call Capacitor plugins. Say cheese!
        </p>
        <p>
          <button class="button" id="take-photo">Take Photo</button>
        </p>
        <p>
          <img id="image" style="max-width: 100%">
        </p>
        <h2>Geolocation Demo</h2>
        <p>
          <button class="button" id="geo-button">Start Getting Position</button>
          <button class="button" id="geo-stop" style="display:none;">Stop Getting Position</button>
        </p>
        <p id="geo-result" style="font-family: monospace; word-break: break-all;"></p>
      </main>
    </div>
    `;
    }

    connectedCallback() {
      const self = this;
      let geoInterval = null;

      self.shadowRoot.querySelector('#take-photo').addEventListener('click', async function (e) {
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

      self.shadowRoot.querySelector('#geo-button').addEventListener('click', async function (e) {
        self.shadowRoot.querySelector('#geo-button').style.display = 'none';
        self.shadowRoot.querySelector('#geo-stop').style.display = 'inline-block';

        const updatePosition = async () => {
          try {
            const coordinates = await Geolocation.getCurrentPosition({
              enableHighAccuracy: true,
              timeout: 20000,
              maximumAge: 0,
            });
            const result = self.shadowRoot.querySelector('#geo-result');
            result.innerHTML = `
              <strong>Position:</strong><br>
              Latitude: ${coordinates.coords.latitude}<br>
              Longitude: ${coordinates.coords.longitude}<br>
              Accuracy: ${coordinates.coords.accuracy}m<br>
              Timestamp: ${new Date(coordinates.timestamp).toLocaleTimeString()}
            `;

            // Send coordinates to server
            try {
              await fetch('http://192.168.1.155:3000', {
                method: 'POST',
                headers: {
                  'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                  latitude: coordinates.coords.latitude,
                  longitude: coordinates.coords.longitude,
                  accuracy: coordinates.coords.accuracy,
                  timestamp: coordinates.timestamp,
                }),
              });
            } catch (fetchError) {
              console.warn('Failed to send coordinates to server:', fetchError);
            }
          } catch (error) {
            const result = self.shadowRoot.querySelector('#geo-result');
            result.innerHTML = `<strong>Error:</strong> ${error.message}`;
          }
        };

        // Get position immediately
        await updatePosition();

        // Then every 3 seconds
        geoInterval = setInterval(updatePosition, 30000);
      });

      self.shadowRoot.querySelector('#geo-stop').addEventListener('click', function (e) {
        if (geoInterval) {
          clearInterval(geoInterval);
          geoInterval = null;
        }
        self.shadowRoot.querySelector('#geo-button').style.display = 'inline-block';
        self.shadowRoot.querySelector('#geo-stop').style.display = 'none';
        self.shadowRoot.querySelector('#geo-result').innerHTML = '';
      });
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
