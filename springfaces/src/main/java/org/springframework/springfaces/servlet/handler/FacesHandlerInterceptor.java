package org.springframework.springfaces.servlet.handler;

import javax.faces.context.FacesContext;
import javax.faces.context.FacesContextFactory;
import javax.faces.context.Flash;
import javax.faces.lifecycle.Lifecycle;
import javax.faces.lifecycle.LifecycleFactory;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.context.ApplicationContext;
import org.springframework.springfaces.context.SpringFacesContext;
import org.springframework.springfaces.dunno.FacesFactory;
import org.springframework.springfaces.servlet.SpringFacesServletContext;
import org.springframework.springfaces.servlet.view.FacesView;
import org.springframework.springfaces.view.View;
import org.springframework.ui.ModelMap;
import org.springframework.web.context.ServletContextAware;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

public class FacesHandlerInterceptor extends HandlerInterceptorAdapter implements ServletContextAware {

	private ServletContext servletContext;

	public void setServletContext(ServletContext servletContext) {
		this.servletContext = servletContext;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
		new SpringFacesContextImpl(servletContext, request, response, handler);
		return true;
	}

	@Override
	public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
			ModelAndView modelAndView) throws Exception {
		getSpringFacesContext().setModelMap(modelAndView.getModelMap());
	}

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
			throws Exception {
		getSpringFacesContext().release();
	}

	private SpringFacesContextImpl getSpringFacesContext() {
		//FIXME cast check
		return (SpringFacesContextImpl) SpringFacesContext.getCurrentInstance();
	}

	private static class SpringFacesContextImpl extends SpringFacesContext implements SpringFacesServletContext {

		private ServletContext servletContext;
		private HttpServletRequest request;
		private HttpServletResponse response;
		private Object handler;
		private ModelMap modelMap;
		private View rendering;
		private WebApplicationContext applicationContext;

		public SpringFacesContextImpl(ServletContext servletContext, HttpServletRequest request,
				HttpServletResponse response, Object handler) {
			this.servletContext = servletContext;
			this.request = request;
			this.response = response;
			this.handler = handler;
			setCurrentInstance(this);
		}

		public void setModelMap(ModelMap modelMap) {
			this.modelMap = modelMap;
		}

		public void render(FacesView view) {
			this.rendering = view;
			try {
				doWithFacesContextAndLifecycle(new FacesContextAndLifecycleCallback<Object>() {
					public Object doWithFacesContextAndLifeCycle(FacesContext facesContext, Lifecycle lifecycle) {
						//FIXME assert life
						lifecycle.execute(facesContext);
						storeModelMapInFlash(facesContext);
						lifecycle.render(facesContext);
						return null;
					}
				});
			} finally {
				this.rendering = null;
			}
		}

		private void storeModelMapInFlash(FacesContext facesContext) {
			//FIXME perhaps better in viewScope ?  perhaps as model?
			Flash flash = facesContext.getExternalContext().getFlash();
			ModelMap modelMap = this.modelMap;
			if (modelMap == null) {
				modelMap = (ModelMap) flash.get("modelMap");
			}
			if (modelMap != null) {
				flash.put("modelMap", modelMap);
			}
		}

		@Override
		public boolean isRendering() {
			return rendering != null;
		}

		@Override
		public View getRendering() {
			//FIXME check isRendering
			return rendering;
		}

		@Override
		public <T> T doWithFacesContext(FacesContextCallback<T> fcc) {
			return doWithRequiredFacesContext(fcc);
		}

		@Override
		public <T> T doWithRequiredFacesContext(final FacesContextCallback<T> fcc) {
			return doWithFacesContextAndLifecycle(new FacesContextAndLifecycleCallback<T>() {
				public T doWithFacesContextAndLifeCycle(FacesContext facesContext, Lifecycle lifecycle) {
					return fcc.doWithFacesContext(facesContext);
				}
			});
		}

		@Override
		public ApplicationContext getApplicationContext() {
			if (applicationContext == null) {
				applicationContext = asWebApplicationContext(request
						.getAttribute(DispatcherServlet.WEB_APPLICATION_CONTEXT_ATTRIBUTE));
				if (applicationContext == null) {
					applicationContext = asWebApplicationContext(servletContext
							.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE));
				}
				//FIXME assert not null
			}
			return applicationContext;
		}

		private static WebApplicationContext asWebApplicationContext(Object object) {
			if (object == null) {
				return null;
			}
			if (object instanceof RuntimeException) {
				throw (RuntimeException) object;
			}
			if (object instanceof Error) {
				throw (Error) object;
			}
			if (!(object instanceof WebApplicationContext)) {
				throw new IllegalStateException("Root context attribute is not of type WebApplicationContext: "
						+ object);
			}
			return (WebApplicationContext) object;
		}

		public void release() {
			setCurrentInstance(null);
		}

		private <T> T doWithFacesContextAndLifecycle(FacesContextAndLifecycleCallback<T> fcc) {

			FacesContext facesContext = FacesContext.getCurrentInstance();
			if (facesContext != null) {
				//FIXME log warning
				return fcc.doWithFacesContextAndLifeCycle(facesContext, null);
			}

			Lifecycle lifecycle = FacesFactory.get(LifecycleFactory.class).getLifecycle(
					LifecycleFactory.DEFAULT_LIFECYCLE);
			facesContext = FacesFactory.get(FacesContextFactory.class).getFacesContext(servletContext, request,
					response, lifecycle);
			try {
				return fcc.doWithFacesContextAndLifeCycle(facesContext, lifecycle);
			} finally {
				facesContext.release();
			}
		}

		private static interface FacesContextAndLifecycleCallback<T> {
			public T doWithFacesContextAndLifeCycle(FacesContext facesContext, Lifecycle lifecycle);
		}
	}

}