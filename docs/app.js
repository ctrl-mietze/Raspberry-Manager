(() => {
  'use strict';
  const english = document.getElementById('english');
  const german = document.getElementById('german');
  const languageNodes = Array.from(document.querySelectorAll('[data-en][data-de]'));
  function changeLanguage(lang) {
    if(!['en','de'].includes(lang)) lang='en';
    document.documentElement.lang=lang;
    languageNodes.forEach(node => { node.innerHTML=node.dataset[lang] || ''; });
    english.classList.toggle('active',lang==='en');
    german.classList.toggle('active',lang==='de');
    try{ localStorage.setItem('raspberry_manager_lang',lang); }catch{}
  }
  let preference='en';
  try{preference=localStorage.getItem('raspberry_manager_lang')||'en';}catch{}
  changeLanguage(preference);
  english.addEventListener('click',()=>changeLanguage('en'));
  german.addEventListener('click',()=>changeLanguage('de'));
  const menu = document.getElementById('menuToggle');
  const links = document.getElementById('navLinks');
  menu.addEventListener('click',()=>{
    const expanded=links.classList.toggle('is-open');
    menu.setAttribute('aria-expanded',String(expanded));
  });
  links.querySelectorAll('a').forEach(link=>link.addEventListener('click',()=>links.classList.remove('is-open')));
  const dialog=document.getElementById('galleryDialog');
  const preview=document.getElementById('largeImage');
  const caption=document.getElementById('imageCaption');
  document.querySelectorAll('.gallery-image').forEach(button=>{
    button.addEventListener('click',()=>{
      preview.src=button.dataset.image;
      preview.alt=button.querySelector('img')?.alt||'Raspberry Manager screenshot';
      caption.textContent=button.dataset.title||'Raspberry Manager';
      if(typeof dialog.showModal==='function') dialog.showModal();
    });
  });
  document.getElementById('closeGallery').addEventListener('click',()=>dialog.close());
  dialog.addEventListener('click',event=>{
    if(event.target===dialog) dialog.close();
  });
  const reduceMotion=window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  if(!reduceMotion&&'IntersectionObserver' in window){
    const observer=new IntersectionObserver(entries=>{
      entries.forEach(entry=>{
        if(entry.isIntersecting){entry.target.classList.add('visible');observer.unobserve(entry.target);}
      });
    },{threshold:.09});
    document.querySelectorAll('.feature,.gallery-grid figure,.download-card,.workbench-shot')
      .forEach(node=>{node.classList.add('reveal');observer.observe(node);});
  }
})();