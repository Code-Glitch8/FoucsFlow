import React from 'react';
import ReactDOM from 'react-dom/client';
import { FocusFlowProvider } from './store/FocusFlowStore';
import { App } from './App';
import './index.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <FocusFlowProvider>
      <App />
    </FocusFlowProvider>
  </React.StrictMode>,
);
